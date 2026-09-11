/* =====================================================
   ORACLE DATA STUDIO
   Frontend JavaScript
===================================================== */


/* =====================================================
   HELPERS
===================================================== */

const $ = (id) => document.getElementById(id);


const state = {

    tables: [],

    columns: [],

    rows: [],

    table: "",

    primaryKeys: [],

    currentRow: null

};


/* =====================================================
   DATE FORMATTER
===================================================== */

/*
   Oracle DATE values are being returned by the backend
   as ISO timestamps such as:

   2026-04-01T18:30:00.000+00:00

   In Indian time this represents:

   02-Apr-2026

   This function converts date columns into:
   DD-MMM-YYYY
*/

function formatDate(value) {

    if (value === null || value === undefined || value === "") {

        return value;

    }


    const date = new Date(value);


    if (Number.isNaN(date.getTime())) {

        return value;

    }


    return new Intl.DateTimeFormat("en-GB", {

        day: "2-digit",

        month: "short",

        year: "numeric",

        timeZone: "Asia/Kolkata"

    }).format(date);

}


/* =====================================================
   CHECK WHETHER COLUMN IS A DATE COLUMN
===================================================== */

function isDateColumn(columnName) {

    if (!columnName) {

        return false;

    }


    const name = String(columnName).toUpperCase();


    return (

        name.includes("DATE") ||

        name === "EXPIRY" ||

        name === "INSPDATE" ||

        name === "MOVEDATE" ||

        name === "DELIVERYDATE"

    );

}


/* =====================================================
   FORMAT CELL VALUE
===================================================== */

function formatCellValue(value, columnName) {

    if (value === null || value === undefined) {

        return "NULL";

    }


    if (isDateColumn(columnName)) {

        return formatDate(value);

    }


    return String(value);

}


/* =====================================================
   API HELPER
===================================================== */

async function api(url, options = {}) {

    const response = await fetch(url, {

        ...options,

        headers: {

            "Content-Type": "application/json",

            ...(options.headers || {})

        }

    });


    const text = await response.text();

    let data = {};

    try {

        data = text ? JSON.parse(text) : {};

    } catch {

        data = {

            message: text

        };

    }


    if (!response.ok) {

        throw new Error(

            data.message ||

            data.error ||

            `Request failed (${response.status})`

        );

    }


    return data;

}


/* =====================================================
   ESCAPE HTML
===================================================== */

function escapeHtml(value) {

    return String(value ?? "")

        .replace(/[&<>"']/g, (character) => {

            const map = {

                "&": "&amp;",

                "<": "&lt;",

                ">": "&gt;",

                '"': "&quot;",

                "'": "&#039;"

            };

            return map[character];

        });

}


/* =====================================================
   TOAST MESSAGE
===================================================== */

function showToast(message) {

    const toast = $("toast");

    toast.textContent = message;

    toast.classList.remove("hidden");


    setTimeout(() => {

        toast.classList.add("hidden");

    }, 2500);

}


/* =====================================================
   COLUMN NAME
===================================================== */

function getColumnName(column) {

    return (

        column.name ||

        column.columnName ||

        column.COLUMN_NAME

    );

}


/* =====================================================
   LOAD TABLES
===================================================== */

async function loadTables() {

    try {

        const data = await api("/api/tables");


        state.tables = Array.isArray(data)

            ? data

            : (data.tables || []);


        $("tableCount").textContent =

            state.tables.length;


        /* DROPDOWN */

        $("tableSelect").innerHTML =

            `<option value="">
                Choose a table...
            </option>` +

            state.tables.map(

                table => `

                    <option value="${escapeHtml(table)}">

                        ${escapeHtml(table)}

                    </option>

                `

            ).join("");


        /* SIDEBAR */

        $("tableList").innerHTML =

            state.tables.map(

                table => `

                    <button
                        data-table="${escapeHtml(table)}">

                        ${escapeHtml(table)}

                    </button>

                `

            ).join("");


        document

            .querySelectorAll("#tableList button")

            .forEach(button => {

                button.addEventListener(

                    "click",

                    () => {

                        $("tableSelect").value =

                            button.dataset.table;


                        loadTable(

                            button.dataset.table

                        );

                    }

                );

            });


    } catch (error) {

        showToast(error.message);

    }

}


/* =====================================================
   LOAD SELECTED TABLE
===================================================== */

async function loadTable(

    tableName = $("tableSelect").value

) {

    if (!tableName) {

        showToast("Choose a table first.");

        return;

    }


    state.table = tableName;


    /* SIDEBAR ACTIVE */

    document

        .querySelectorAll("#tableList button")

        .forEach(button => {

            button.classList.toggle(

                "active",

                button.dataset.table === tableName

            );

        });


    try {

        const [columns, rows] =

            await Promise.all([

                api(

                    `/api/tables/${encodeURIComponent(
                        tableName
                    )}/columns`

                ),

                api(

                    `/api/tables/${encodeURIComponent(
                        tableName
                    )}/rows`

                )

            ]);


        state.columns =

            columns.columns || columns || [];


        state.rows =

            rows.rows || rows || [];


        /* PRIMARY KEYS */

        state.primaryKeys =

            state.columns

                .filter(column =>

                    column.primaryKey ||

                    column.primary ||

                    column.isPrimaryKey

                )

                .map(getColumnName);


        $("tableTitle").textContent =

            tableName;


        $("tableMeta").textContent =

            `${state.rows.length} records · ` +

            `${state.columns.length} columns`;


        renderTable();


    } catch (error) {

        showToast(error.message);

    }

}


/* =====================================================
   RENDER TABLE
===================================================== */

function renderTable() {

    if (!state.rows.length) {

        $("tableWrap").className =

            "table-container empty-container";


        $("tableWrap").innerHTML = `

            <div class="empty-state">

                <div class="empty-icon">
                    ∅
                </div>

                <h3>
                    No records found
                </h3>

                <p>
                    This table is currently empty.
                </p>

            </div>

        `;

        return;

    }


    const columnNames =

        state.columns.map(getColumnName);


    $("tableWrap").className =

        "table-container";


    $("tableWrap").innerHTML = `

        <table class="data-table">

            <thead>

                <tr>

                    ${columnNames.map(

                        column => `

                            <th>

                                ${escapeHtml(column)}

                            </th>

                        `

                    ).join("")}


                    <th>
                        Actions
                    </th>

                </tr>

            </thead>


            <tbody>

                ${state.rows.map(

                    (row, index) => `

                        <tr>

                            ${columnNames.map(

                                column => {

                                    const rawValue =

                                        row[column];

                                    const displayValue =

                                        formatCellValue(
                                            rawValue,
                                            column
                                        );


                                    return `

                                        <td
                                            title="${escapeHtml(
                                                displayValue
                                            )}">

                                            ${
                                                rawValue === null
                                                    ? "NULL"
                                                    : escapeHtml(
                                                        displayValue
                                                    )
                                            }

                                        </td>

                                    `;

                                }

                            ).join("")}


                            <td>

                                <div class="actions">

                                    <button
                                        class="row-button"
                                        data-edit="${index}">

                                        Edit

                                    </button>


                                    <button
                                        class="row-button delete-button"
                                        data-delete="${index}">

                                        Delete

                                    </button>

                                </div>

                            </td>

                        </tr>

                    `

                ).join("")}

            </tbody>

        </table>

    `;


    /* EDIT */

    document

        .querySelectorAll("[data-edit]")

        .forEach(button => {

            button.addEventListener(

                "click",

                () => {

                    openEditModal(

                        Number(
                            button.dataset.edit
                        )

                    );

                }

            );

        });


    /* DELETE */

    document

        .querySelectorAll("[data-delete]")

        .forEach(button => {

            button.addEventListener(

                "click",

                () => {

                    deleteRow(

                        Number(
                            button.dataset.delete
                        )

                    );

                }

            );

        });

}


/* =====================================================
   EDIT MODAL
===================================================== */

function openEditModal(index) {

    state.currentRow =

        state.rows[index];


    $("modalTitle").textContent =

        `Edit ${state.table}`;


    $("editFields").innerHTML =

        state.columns.map(column => {

            const name =

                getColumnName(column);


            const isPrimaryKey =

                state.primaryKeys.includes(name);


            let value =

                state.currentRow[name] ?? "";


            /*
               Keep the original raw value in the input.
               This is important so the backend receives
               the original database-compatible value.
            */

            if (isDateColumn(name) && value) {

                const date = new Date(value);


                if (!Number.isNaN(date.getTime())) {

                    value =

                        date.toISOString();

                }

            }


            return `

                <div
                    class="field ${
                        isPrimaryKey
                            ? "locked"
                            : ""
                    }">

                    <label>

                        ${escapeHtml(name)}

                        ${
                            isPrimaryKey
                                ? " · PRIMARY KEY"
                                : ""
                        }

                    </label>


                    <input

                        data-field="${escapeHtml(name)}"

                        value="${escapeHtml(value)}"

                        ${
                            isPrimaryKey
                                ? "disabled"
                                : ""
                        }

                    />

                </div>

            `;

        }).join("");


    $("modal")

        .classList

        .remove("hidden");

}


/* =====================================================
   SAVE EDIT
===================================================== */

async function saveEdit() {

    if (!state.currentRow) {

        return;

    }


    const queryParams =

        new URLSearchParams();


    /* PRIMARY KEY VALUES */

    state.primaryKeys.forEach(

        key => {

            queryParams.set(

                key,

                state.currentRow[key]

            );

        }

    );


    /* NEW VALUES */

    const updatedValues = {};


    document

        .querySelectorAll("#editFields input")

        .forEach(input => {

            updatedValues[

                input.dataset.field

            ] = input.value;

        });


    try {

        await api(

            `/api/tables/${encodeURIComponent(
                state.table
            )}?${queryParams}`,

            {

                method: "PUT",

                body: JSON.stringify(

                    updatedValues

                )

            }

        );


        $("modal")

            .classList

            .add("hidden");


        showToast(

            "Record updated successfully."

        );


        await loadTable(

            state.table

        );


    } catch (error) {

        showToast(error.message);

    }

}


/* =====================================================
   DELETE ROW
===================================================== */

async function deleteRow(index) {

    if (!state.primaryKeys.length) {

        showToast(

            "Delete requires a primary key."

        );

        return;

    }


    const row =

        state.rows[index];


    const queryParams =

        new URLSearchParams();


    state.primaryKeys.forEach(

        key => {

            queryParams.set(

                key,

                row[key]

            );

        }

    );


    const confirmed =

        confirm(

            "Delete this record?"

        );


    if (!confirmed) {

        return;

    }


    try {

        await api(

            `/api/tables/${encodeURIComponent(
                state.table
            )}?${queryParams}`,

            {

                method: "DELETE"

            }

        );


        showToast(

            "Record deleted successfully."

        );


        await loadTable(

            state.table

        );


    } catch (error) {

        showToast(error.message);

    }

}


/* =====================================================
   PAGE NAVIGATION
===================================================== */

function showPage(page) {

    document

        .querySelectorAll(".nav-button")

        .forEach(button => {

            button.classList.toggle(

                "active",

                button.dataset.page === page

            );

        });


    document

        .querySelectorAll(".page")

        .forEach(section => {

            section.classList.toggle(

                "active",

                section.id === `${page}Page`

            );

        });


    $("pageTitle").textContent =

        page === "sql"

            ? "SQL Console"

            : "Data Explorer";

}


/* =====================================================
   RUN SQL
===================================================== */

async function runSQL() {

    const sql =

        $("sqlInput")

            .value

            .trim();


    if (!sql) {

        showToast(

            "Enter a SQL statement first."

        );

        return;

    }


    try {

        const data =

            await api(

                "/api/query",

                {

                    method: "POST",

                    body: JSON.stringify({

                        sql: sql

                    })

                }

            );


        /* SELECT / WITH */

        if (

            Array.isArray(data) ||

            Array.isArray(data.rows)

        ) {

            const rows =

                Array.isArray(data)

                    ? data

                    : data.rows;


            const columns =

                rows.length

                    ? Object.keys(rows[0])

                    : (

                        data.columns || []

                    );


            $("resultTitle").textContent =

                `${rows.length} rows returned`;


            if (!rows.length) {

                $("resultWrap").innerHTML = `

                    <div class="empty-state">

                        <h3>
                            No rows returned
                        </h3>

                    </div>

                `;

                return;

            }


            $("resultWrap").innerHTML = `

                <table class="result-table">

                    <thead>

                        <tr>

                            ${columns.map(

                                column => `

                                    <th>

                                        ${escapeHtml(
                                            column
                                        )}

                                    </th>

                                `

                            ).join("")}

                        </tr>

                    </thead>


                    <tbody>

                        ${rows.map(

                            row => `

                                <tr>

                                    ${columns.map(

                                        column => {

                                            const rawValue =

                                                row[column];


                                            const displayValue =

                                                formatCellValue(
                                                    rawValue,
                                                    column
                                                );


                                            return `

                                                <td
                                                    title="${escapeHtml(
                                                        displayValue
                                                    )}">

                                                    ${
                                                        rawValue === null
                                                            ? "NULL"
                                                            : escapeHtml(
                                                                displayValue
                                                            )
                                                    }

                                                </td>

                                            `;

                                        }

                                    ).join("")}

                                </tr>

                            `

                        ).join("")}

                    </tbody>

                </table>

            `;


            return;

        }


        /* INSERT / UPDATE / DELETE */

        const affectedRows =

            data.affectedRows ??

            data.rowsAffected ??

            data.count ??

            0;


        $("resultTitle").textContent =

            "Statement executed";


        $("resultWrap").innerHTML = `

            <div class="affected">

                Affected rows

                <strong>

                    ${escapeHtml(

                        affectedRows

                    )}

                </strong>

            </div>

        `;


        if (state.table) {

            await loadTable(

                state.table

            );

        }


    } catch (error) {

        $("resultTitle").textContent =

            "Query failed";


        $("resultWrap").innerHTML = `

            <div class="affected">

                <strong>
                    Error
                </strong>

                ${escapeHtml(

                    error.message

                )}

            </div>

        `;

    }

}


/* =====================================================
   EVENT LISTENERS
===================================================== */


/* NAVIGATION */

document

    .querySelectorAll(".nav-button")

    .forEach(button => {

        button.addEventListener(

            "click",

            () => {

                showPage(

                    button.dataset.page

                );

            }

        );

    });


/* OPEN TABLE */

$("openTable")

    .addEventListener(

        "click",

        () => {

            loadTable();

        }

    );


/* REFRESH */

$("refresh")

    .addEventListener(

        "click",

        async () => {

            if (state.table) {

                await loadTable(

                    state.table

                );

            } else {

                await loadTables();

            }

        }

    );


/* OPEN SQL */

$("openSql")

    .addEventListener(

        "click",

        () => {

            showPage("sql");

            $("sqlInput").focus();

        }

    );


/* INSERT WITH SQL */

$("insertSql")

    .addEventListener(

        "click",

        () => {

            showPage("sql");


            $("sqlInput").value =

                `INSERT INTO ${
                    state.table || "TABLE_NAME"
                } (...) VALUES (...);`;


            $("sqlInput").focus();

        }

    );


/* RUN SQL */

$("runSql")

    .addEventListener(

        "click",

        runSQL

    );


/* CLEAR SQL */

$("clearSql")

    .addEventListener(

        "click",

        () => {

            $("sqlInput").value = "";


            $("resultTitle").textContent =

                "No query executed";


            $("resultWrap").innerHTML = `

                <div class="empty-state">

                    <div class="empty-icon">
                        ⌘
                    </div>

                    <h3>

                        Query results appear here

                    </h3>

                    <p>

                        Run a statement to see
                        rows or affected-row counts.

                    </p>

                </div>

            `;

        }

    );


/* CLOSE MODAL */

$("closeModal")

    .addEventListener(

        "click",

        () => {

            $("modal")

                .classList

                .add("hidden");

        }

    );


/* CANCEL */

$("cancel")

    .addEventListener(

        "click",

        () => {

            $("modal")

                .classList

                .add("hidden");

        }

    );


/* SAVE */

$("save")

    .addEventListener(

        "click",

        saveEdit

    );


/* CTRL + ENTER */

$("sqlInput")

    .addEventListener(

        "keydown",

        event => {

            if (

                (event.ctrlKey ||
                 event.metaKey) &&

                event.key === "Enter"

            ) {

                event.preventDefault();

                runSQL();

            }

        }

    );


/* =====================================================
   INITIAL LOAD
===================================================== */

loadTables();