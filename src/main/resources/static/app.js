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
   CHECK DATE COLUMN
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
   FORMAT CELL
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
   TOAST
===================================================== */

function showToast(message) {

    const toast = $("toast");

    if (!toast) {
        return;
    }

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


        if ($("tableCount")) {
            $("tableCount").textContent =
                state.tables.length;
        }


        if ($("tableCountSmall")) {
            $("tableCountSmall").textContent =
                state.tables.length;
        }


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

        // Automatically open default table so Data Explorer immediately displays records
        if (state.tables.length > 0 && !state.table) {
            const initialTable = state.tables.includes("PRODUCT")
                ? "PRODUCT"
                : state.tables[0];
            $("tableSelect").value = initialTable;
            loadTable(initialTable);
        }

    } catch (error) {

        showToast(error.message);

    }

}


/* =====================================================
   LOAD TABLE
===================================================== */

async function loadTable(
    tableName = $("tableSelect").value
) {

    if (!tableName) {

        showToast("Choose a table first.");

        return;

    }


    state.table = tableName;


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


        state.primaryKeys =
            columns.primaryKeys || [];


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


    document
        .querySelectorAll("[data-edit]")
        .forEach(button => {

            button.addEventListener(
                "click",
                () => {

                    openEditModal(
                        Number(button.dataset.edit)
                    );

                }
            );

        });


    document
        .querySelectorAll("[data-delete]")
        .forEach(button => {

            button.addEventListener(
                "click",
                () => {

                    deleteRow(
                        Number(button.dataset.delete)
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


            if (isDateColumn(name) && value) {

                const date = new Date(value);

                if (!Number.isNaN(date.getTime())) {
                    value = date.toISOString();
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


    state.primaryKeys.forEach(
        key => {

            queryParams.set(
                key,
                state.currentRow[key]
            );

        }
    );


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
                body: JSON.stringify(updatedValues)
            }
        );


        $("modal")
            .classList
            .add("hidden");


        showToast(
            "Record updated successfully."
        );


        await loadTable(state.table);


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


    if (!confirm("Delete this record?")) {
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


        await loadTable(state.table);


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


    const titles = {
        explorer: "Data Explorer",
        sql: "SQL Console",
        graph: "Neo4j Graph Workbench (Modern DB)",
        triggers: "Database Triggers",
        cursors: "Database Cursors",
        eer: "ER & Enhanced ER (EER) Studio",
        cloud: "Cloud Deployment & Architecture"
    };

    $("pageTitle").textContent =
        titles[page] || "Data Explorer";

    if (page === "graph") {
        initGraphWorkbench();
        setTimeout(() => {
            if (typeof resizeGraphCanvas === "function") {
                resizeGraphCanvas();
            }
        }, 60);
    } else if (page === "eer") {
        initEerStudio();
    } else if (page === "cloud") {
        checkCloudHealth();
    } else if (page === "triggers") {
        loadTriggers();
    } else if (page === "cursors") {
        loadAllCursors();
    }
}


/* =====================================================
   RUN SQL
===================================================== */

async function runSQL() {

    const sql =
        $("sqlInput").value.trim();


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
                    : (data.columns || []);


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
                                        ${escapeHtml(column)}
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

                                                <td>

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
                    ${escapeHtml(affectedRows)}
                </strong>

            </div>

        `;


        if (state.table) {
            await loadTable(state.table);
        }


    } catch (error) {

        $("resultTitle").textContent =
            "Query failed";


        $("resultWrap").innerHTML = `

            <div class="affected">

                <strong>
                    Error
                </strong>

                ${escapeHtml(error.message)}

            </div>

        `;

    }

}


/* =====================================================
   TRIGGERS
===================================================== */

async function loadTriggers() {

    const container =
        $("triggerWrap");


    if (!container) {
        return;
    }


    container.innerHTML = `

        <div class="empty-state">

            <div class="empty-icon">
                ⚡
            </div>

            <h3>
                Loading triggers...
            </h3>

        </div>

    `;


    try {

        const data =
            await api("/api/triggers");


        const triggers =
            data.triggers || [];


        if (!triggers.length) {

            container.innerHTML = `

                <div class="empty-state">

                    <h3>
                        No triggers found
                    </h3>

                </div>

            `;

            return;

        }


        container.innerHTML = `

            <table class="result-table">

                <thead>

                    <tr>

                        <th>
                            Trigger Name
                        </th>

                        <th>
                            Status
                        </th>

                        <th>
                            Event
                        </th>

                        <th>
                            Trigger Type
                        </th>

                    </tr>

                </thead>


                <tbody>

                    ${triggers.map(
                        trigger => {

                            const name =
                                trigger.TRIGGER_NAME ??
                                trigger.trigger_name ??
                                trigger.Trigger_Name ??
                                "";

                            const status =
                                trigger.STATUS ??
                                trigger.status ??
                                "";

                            const event =
                                trigger.TRIGGERING_EVENT ??
                                trigger.triggering_event ??
                                "";

                            const type =
                                trigger.TRIGGER_TYPE ??
                                trigger.trigger_type ??
                                "";


                            return `

                                <tr>

                                    <td>
                                        <strong>
                                            ${escapeHtml(name)}
                                        </strong>
                                    </td>

                                    <td>

                                        <span class="trigger-status ${
                                            String(status).toUpperCase() === "ENABLED"
                                                ? "enabled"
                                                : "disabled"
                                        }">

                                            ${escapeHtml(status)}

                                        </span>

                                    </td>

                                    <td>
                                        ${escapeHtml(event)}
                                    </td>

                                    <td>
                                        ${escapeHtml(type)}
                                    </td>

                                </tr>

                            `;

                        }
                    ).join("")}

                </tbody>

            </table>

        `;


    } catch (error) {

        container.innerHTML = `

            <div class="empty-state">

                <div class="empty-icon">
                    ⚠
                </div>

                <h3>
                    Could not load triggers
                </h3>

                <p>
                    ${escapeHtml(error.message)}
                </p>

            </div>

        `;

    }

}


/* =====================================================
   CURSOR TABLE RENDERER
===================================================== */

function renderCursorResult(
    containerId,
    rows,
    emptyMessage = "No rows returned."
) {

    const container =
        $(containerId);


    if (!container) {
        return;
    }


    if (!rows || !rows.length) {

        container.innerHTML = `

            <div class="empty-state">

                <h3>
                    No rows returned
                </h3>

                <p>
                    ${escapeHtml(emptyMessage)}
                </p>

            </div>

        `;

        return;

    }


    const columns =
        Object.keys(rows[0]);


    container.innerHTML = `

        <table class="result-table">

            <thead>

                <tr>

                    ${columns.map(
                        column => `
                            <th>
                                ${escapeHtml(column)}
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

                                    const value =
                                        row[column];

                                    return `

                                        <td>

                                            ${
                                                value === null
                                                    ? "NULL"
                                                    : escapeHtml(
                                                        formatCellValue(
                                                            value,
                                                            column
                                                        )
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

}


/* =====================================================
   CURSOR 1
   CUSTOMER CURSOR
===================================================== */

async function runCustomerCursor() {
    const wrap = $("customerCursorResult");
    if (wrap) {
        wrap.innerHTML = `<div class="empty-state"><div class="empty-icon">⏳</div><h3>Executing Customer Cursor...</h3></div>`;
    }
    try {
        const data = await api("/api/cursors/customers");
        renderCursorResult(
            "customerCursorResult",
            data.rows,
            "No customers found."
        );
    } catch (error) {
        if (wrap) {
            wrap.innerHTML = `<div class="affected"><strong>Cursor Error: </strong>${escapeHtml(error.message)}</div>`;
        }
        showToast(error.message);
    }
}


/* =====================================================
   CURSOR 2
   PARAMETERIZED ORDER CURSOR
===================================================== */

async function runOrderCursor() {
    const customerId = $("cursorCustomerId") ? $("cursorCustomerId").value.trim() : "";

    if (!customerId) {
        showToast("Enter a customer ID.");
        return;
    }

    const wrap = $("orderCursorResult");
    if (wrap) {
        wrap.innerHTML = `<div class="empty-state"><div class="empty-icon">⏳</div><h3>Executing Order Cursor for ${escapeHtml(customerId)}...</h3></div>`;
    }

    try {
        const data = await api(
            `/api/cursors/orders?custId=${encodeURIComponent(customerId)}`
        );
        const rows = data.rows || [];
        const emptyMsg = rows.length
            ? ""
            : `No orders found for customer "${customerId}". Valid customers in database: C-01 (Shama Retail), C-03 (Metro Mart), C-05 (Anand Stores).`;

        renderCursorResult(
            "orderCursorResult",
            rows,
            emptyMsg
        );
    } catch (error) {
        if (wrap) {
            wrap.innerHTML = `<div class="affected"><strong>Cursor Error: </strong>${escapeHtml(error.message)}</div>`;
        }
        showToast(error.message);
    }
}


/* =====================================================
   CURSOR 3
   ORDER TOTAL CURSOR
===================================================== */

async function runOrderTotalCursor() {
    const wrap = $("orderTotalCursorResult");
    if (wrap) {
        wrap.innerHTML = `<div class="empty-state"><div class="empty-icon">⏳</div><h3>Executing Order Total Cursor...</h3></div>`;
    }
    try {
        const data = await api("/api/cursors/order-totals");
        renderCursorResult(
            "orderTotalCursorResult",
            data.rows,
            "No order totals found."
        );
    } catch (error) {
        if (wrap) {
            wrap.innerHTML = `<div class="affected"><strong>Cursor Error: </strong>${escapeHtml(error.message)}</div>`;
        }
        showToast(error.message);
    }
}

async function loadAllCursors() {
    runCustomerCursor();
    runOrderCursor();
    runOrderTotalCursor();
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


                /* Load trigger data when opened */

                if (
                    button.dataset.page === "triggers"
                ) {

                    loadTriggers();

                }

            }
        );

    });


/* OPEN TABLE */

if ($("openTable")) {
    $("openTable").addEventListener(
        "click",
        () => {
            loadTable();
        }
    );
}

/* TABLE SELECT DROPDOWN AUTO-LOAD */
if ($("tableSelect")) {
    $("tableSelect").addEventListener(
        "change",
        (e) => {
            if (e.target.value) {
                loadTable(e.target.value);
            }
        }
    );
}


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


/* REFRESH TRIGGERS */

if ($("refreshTriggers")) {

    $("refreshTriggers")
        .addEventListener(
            "click",
            loadTriggers
        );

}


/* CUSTOMER CURSOR */

if ($("runCustomerCursor")) {

    $("runCustomerCursor")
        .addEventListener(
            "click",
            runCustomerCursor
        );

}


/* PARAMETERIZED CURSOR */

if ($("runOrderCursor")) {

    $("runOrderCursor")
        .addEventListener(
            "click",
            runOrderCursor
        );

}

if ($("cursorCustomerSelect")) {
    $("cursorCustomerSelect").addEventListener("change", (e) => {
        if ($("cursorCustomerId")) {
            $("cursorCustomerId").value = e.target.value;
        }
        runOrderCursor();
    });
}

document.querySelectorAll(".cursor-quick-pill").forEach(pill => {
    pill.addEventListener("click", () => {
        const val = pill.dataset.cust;
        if ($("cursorCustomerId")) $("cursorCustomerId").value = val;
        if ($("cursorCustomerSelect")) $("cursorCustomerSelect").value = val;
        runOrderCursor();
    });
});


/* ORDER TOTAL CURSOR */

if ($("runOrderTotalCursor")) {

    $("runOrderTotalCursor")
        .addEventListener(
            "click",
            runOrderTotalCursor
        );

}


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
checkCloudHealth();


/* =====================================================
   MODERN DATABASE: NEO4J GRAPH WORKBENCH
===================================================== */

const FALLBACK_GRAPH_NODES = [
    { id: "SUPPLIER_S01", label: "Supplier S01 (Apex Agro)", type: "Supplier", properties: { id: "S-01", name: "Apex Agro Supplies", city: "Chennai" } },
    { id: "SUPPLIER_S03", label: "Supplier S03 (Metro Logistics)", type: "Supplier", properties: { id: "S-03", name: "Metro Wholesale Mills", city: "Mumbai" } },
    { id: "SUPPLIER_S05", label: "Supplier S05 (Delta Chem)", type: "Supplier", properties: { id: "S-05", name: "Delta Cold & Chemicals", city: "Delhi" } },
    { id: "PRODUCT_P100", label: "Basmati Rice [P-100]", type: "Product", properties: { id: "P-100", name: "Basmati Rice", category: "Grains", unitPrice: 480 } },
    { id: "PRODUCT_P104", label: "Sunflower Oil [P-104]", type: "Product", properties: { id: "P-104", name: "Sunflower Oil", category: "Oils", unitPrice: 132 } },
    { id: "PRODUCT_P108", label: "Sona Masoori [P-108]", type: "Product", properties: { id: "P-108", name: "Sona Masoori Rice", category: "Staples", unitPrice: 445 } },
    { id: "PRODUCT_P210", label: "Frozen Peas [P-210]", type: "PerishableProduct", properties: { id: "P-210", name: "Frozen Peas", category: "Frozen", unitPrice: 96 } },
    { id: "PRODUCT_P301", label: "Industrial Solvent [P-301]", type: "HazardousProduct", properties: { id: "P-301", name: "Industrial Solvent", category: "Chemicals", unitPrice: 1250 } },
    { id: "WAREHOUSE_W01", label: "Chennai Central Hub [W-01]", type: "Warehouse", properties: { id: "W-01", name: "Chennai Central Hub", city: "Chennai", capacity: 50000 } },
    { id: "WAREHOUSE_W02", label: "Mumbai Port Depot [W-02]", type: "Warehouse", properties: { id: "W-02", name: "Mumbai Port Depot", city: "Mumbai", capacity: 75000 } },
    { id: "CUSTOMER_C01", label: "Shama Retail [C-01]", type: "Customer", properties: { id: "C-01", name: "Shama Retail", city: "Chennai" } },
    { id: "CUSTOMER_C03", label: "Metro Mart [C-03]", type: "Customer", properties: { id: "C-03", name: "Metro Mart", city: "Mumbai" } },
    { id: "CUSTOMER_C05", label: "Anand Stores [C-05]", type: "Customer", properties: { id: "C-05", name: "Anand Stores", city: "Chennai" } },
    { id: "ORDER_O101", label: "Order #O-101", type: "Order", properties: { id: "O-101", date: "2026-04-02", status: "SHIPPED", amount: 16200, customerName: "Shama Retail" } },
    { id: "ORDER_O102", label: "Order #O-102", type: "Order", properties: { id: "O-102", date: "2026-04-05", status: "NEW", amount: 2880, customerName: "Metro Mart" } },
    { id: "ORDER_O103", label: "Order #O-103", type: "Order", properties: { id: "O-103", date: "2026-04-09", status: "SHIPPED", amount: 7200, customerName: "Shama Retail" } },
    { id: "ORDER_O104", label: "Order #O-104", type: "Order", properties: { id: "O-104", date: "2026-04-11", status: "NEW", amount: 5340, customerName: "Anand Stores" } },
    { id: "ORDER_O105", label: "Order #O-105", type: "Order", properties: { id: "O-105", date: "2026-04-14", status: "SHIPPED", amount: 7140, customerName: "Metro Mart" } },
    { id: "ORDER_O106", label: "Order #O-106", type: "Order", properties: { id: "O-106", date: "2026-04-16", status: "NEW", amount: 8640, customerName: "Anand Stores" } },
    { id: "DRIVER_D05", label: "Driver D-05 (Ramesh)", type: "Driver", properties: { id: "D-05", name: "Ramesh Kumar", license: "TN-02-2021-998" } },
    { id: "DRIVER_D09", label: "Driver D-09 (Suresh)", type: "Driver", properties: { id: "D-09", name: "Suresh Patil", license: "MH-01-2020-412" } },
    { id: "DRIVER_D11", label: "Driver D-11 (Venkatesh)", type: "Driver", properties: { id: "D-11", name: "Venkatesh Rao", license: "TN-05-2022-771" } },
    { id: "MANAGER_M01", label: "Manager M-01 (Kavita)", type: "Manager", properties: { id: "M-01", name: "Kavita Sharma", role: "Warehouse Director" } }
];

const FALLBACK_GRAPH_LINKS = [
    { source: "SUPPLIER_S01", target: "PRODUCT_P100", label: "SUPPLIES", properties: {} },
    { source: "SUPPLIER_S01", target: "PRODUCT_P104", label: "SUPPLIES", properties: {} },
    { source: "SUPPLIER_S03", target: "PRODUCT_P108", label: "SUPPLIES", properties: {} },
    { source: "SUPPLIER_S03", target: "PRODUCT_P210", label: "SUPPLIES", properties: {} },
    { source: "SUPPLIER_S05", target: "PRODUCT_P301", label: "SUPPLIES", properties: {} },
    { source: "WAREHOUSE_W01", target: "PRODUCT_P100", label: "STOCKS", properties: { quantity: 1200 } },
    { source: "WAREHOUSE_W01", target: "PRODUCT_P108", label: "STOCKS", properties: { quantity: 850 } },
    { source: "WAREHOUSE_W01", target: "PRODUCT_P210", label: "STOCKS", properties: { quantity: 400 } },
    { source: "WAREHOUSE_W02", target: "PRODUCT_P104", label: "STOCKS", properties: { quantity: 2500 } },
    { source: "WAREHOUSE_W02", target: "PRODUCT_P301", label: "STOCKS", properties: { quantity: 150 } },
    { source: "CUSTOMER_C01", target: "ORDER_O101", label: "PLACED", properties: {} },
    { source: "CUSTOMER_C03", target: "ORDER_O102", label: "PLACED", properties: {} },
    { source: "CUSTOMER_C01", target: "ORDER_O103", label: "PLACED", properties: {} },
    { source: "CUSTOMER_C05", target: "ORDER_O104", label: "PLACED", properties: {} },
    { source: "CUSTOMER_C03", target: "ORDER_O105", label: "PLACED", properties: {} },
    { source: "CUSTOMER_C05", target: "ORDER_O106", label: "PLACED", properties: {} },
    { source: "ORDER_O101", target: "PRODUCT_P100", label: "CONTAINS", properties: { price: 480, qty: 20 } },
    { source: "ORDER_O101", target: "PRODUCT_P104", label: "CONTAINS", properties: { price: 132, qty: 50 } },
    { source: "ORDER_O102", target: "PRODUCT_P210", label: "CONTAINS", properties: { price: 96, qty: 30 } },
    { source: "ORDER_O103", target: "PRODUCT_P100", label: "CONTAINS", properties: { price: 480, qty: 15 } },
    { source: "ORDER_O104", target: "PRODUCT_P108", label: "CONTAINS", properties: { price: 445, qty: 12 } },
    { source: "ORDER_O105", target: "PRODUCT_P104", label: "CONTAINS", properties: { price: 132, qty: 25 } },
    { source: "ORDER_O105", target: "PRODUCT_P210", label: "CONTAINS", properties: { price: 96, qty: 40 } },
    { source: "ORDER_O106", target: "PRODUCT_P100", label: "CONTAINS", properties: { price: 480, qty: 18 } },
    { source: "DRIVER_D05", target: "ORDER_O101", label: "DELIVERS", properties: { assignedCity: "Chennai" } },
    { source: "DRIVER_D09", target: "ORDER_O102", label: "DELIVERS", properties: { assignedCity: "Mumbai" } },
    { source: "DRIVER_D05", target: "ORDER_O103", label: "DELIVERS", properties: { assignedCity: "Chennai" } },
    { source: "DRIVER_D05", target: "ORDER_O104", label: "DELIVERS", properties: { assignedCity: "Chennai" } },
    { source: "DRIVER_D09", target: "ORDER_O105", label: "DELIVERS", properties: { assignedCity: "Mumbai" } },
    { source: "DRIVER_D11", target: "ORDER_O106", label: "DELIVERS", properties: { assignedCity: "Chennai" } },
    { source: "MANAGER_M01", target: "WAREHOUSE_W01", label: "MANAGES", properties: { since: "2024" } }
];

const graphState = {
    initialized: false,
    nodes: FALLBACK_GRAPH_NODES.map((n, i) => {
        const angle = (i / FALLBACK_GRAPH_NODES.length) * Math.PI * 2;
        return {
            ...n,
            x: 400 + Math.cos(angle) * 160,
            y: 260 + Math.sin(angle) * 160,
            vx: 0,
            vy: 0,
            radius: 17
        };
    }),
    links: [...FALLBACK_GRAPH_LINKS],
    filter: "ALL",
    hoveredNode: null,
    selectedNode: null,
    dragNode: null,
    isDragging: false,
    panX: 0,
    panY: 0,
    zoom: 1,
    lastMouseX: 0,
    lastMouseY: 0,
    highlightedNodeIds: null,
    highlightedLinkKeys: null,
    animating: true
};

const NODE_COLORS = {
    Supplier: "#8b5cf6",
    Product: "#10b981",
    PerishableProduct: "#059669",
    HazardousProduct: "#ef4444",
    Warehouse: "#3b82f6",
    Order: "#06b6d4",
    Customer: "#f59e0b",
    Driver: "#f97316",
    Manager: "#ec4899",
    Default: "#64748b"
};

function getNodeColor(type) {
    if (!type) return NODE_COLORS.Default;
    const str = String(type);
    if (NODE_COLORS[str]) return NODE_COLORS[str];
    const lower = str.toLowerCase();
    if (lower.includes("supplier")) return NODE_COLORS.Supplier;
    if (lower.includes("hazard")) return NODE_COLORS.HazardousProduct;
    if (lower.includes("perish")) return NODE_COLORS.PerishableProduct;
    if (lower.includes("product")) return NODE_COLORS.Product;
    if (lower.includes("warehouse")) return NODE_COLORS.Warehouse;
    if (lower.includes("order")) return NODE_COLORS.Order;
    if (lower.includes("customer")) return NODE_COLORS.Customer;
    if (lower.includes("driver")) return NODE_COLORS.Driver;
    if (lower.includes("manager")) return NODE_COLORS.Manager;
    return NODE_COLORS.Default;
}

function getNodeLetter(type) {
    const t = String(type || "").toLowerCase();
    if (t.includes("supplier")) return "S";
    if (t.includes("product")) return "P";
    if (t.includes("warehouse")) return "W";
    if (t.includes("order")) return "O";
    if (t.includes("customer")) return "C";
    if (t.includes("driver")) return "D";
    if (t.includes("manager")) return "M";
    return (String(type || "N")[0] || "N").toUpperCase();
}

async function initGraphWorkbench() {
    if (!graphState.initialized) {
        setupGraphCanvas();
        setupGraphEventListeners();
        graphState.initialized = true;
    }
    await loadGraphData();
    await loadGraphStatus();
    loadBottlenecks();
}

async function loadGraphStatus() {
    try {
        const data = await api("/api/graph/status");
        if ($("graphEngineBadge")) {
            $("graphEngineBadge").textContent = data.liveConnected ? "Neo4j AuraDB (Cloud Active)" : "Built-in Graph Engine (Ready)";
            $("graphEngineBadge").className = data.liveConnected ? "badge badge-green" : "badge badge-purple";
        }
        if ($("openNeo4jModal")) {
            if (data.liveConnected) {
                $("openNeo4jModal").textContent = "✓ AuraDB Connected";
                $("openNeo4jModal").className = "button secondary-button";
            } else {
                $("openNeo4jModal").textContent = "⚡ Connect Live AuraDB";
                $("openNeo4jModal").className = "button primary-button";
            }
        }
        if ($("cloudGraphStatus")) {
            $("cloudGraphStatus").innerHTML = `<span class="badge ${data.liveConnected ? "badge-green" : "badge-purple"}">${escapeHtml(data.engine || "Graph Engine Active")}</span>`;
        }
    } catch (e) {
        if ($("graphEngineBadge")) {
            $("graphEngineBadge").textContent = "Built-in Graph Engine";
        }
    }
}

let animationFrameId = null;

function resizeGraphCanvas() {
    const canvas = $("graphCanvas");
    if (!canvas) return;
    const container = $("canvasContainer") || canvas.parentElement;
    const rect = container ? container.getBoundingClientRect() : canvas.getBoundingClientRect();
    const width = Math.max(400, Math.floor(rect.width || (container && container.clientWidth) || 800));
    const height = Math.max(300, Math.floor(rect.height || (container && container.clientHeight) || 520));
    const dpr = window.devicePixelRatio || 1;

    canvas.width = Math.floor(width * dpr);
    canvas.height = Math.floor(height * dpr);
    canvas.style.width = width + "px";
    canvas.style.height = height + "px";

    const ctx = canvas.getContext("2d");
    if (ctx) {
        ctx.setTransform(1, 0, 0, 1, 0, 0);
        ctx.scale(dpr, dpr);
    }
}

function layoutVisibleNodes() {
    const canvas = $("graphCanvas");
    if (!canvas) return;
    const container = $("canvasContainer") || canvas.parentElement;
    const rect = container ? container.getBoundingClientRect() : { width: 800, height: 520 };
    const width = Math.max(400, Math.floor(rect.width || (container && container.clientWidth) || 800));
    const height = Math.max(300, Math.floor(rect.height || (container && container.clientHeight) || 520));
    const cx = width / 2;
    const cy = height / 2;

    const nodes = getVisibleNodes();
    if (!nodes || !nodes.length) return;

    const count = nodes.length;
    const orbitRadius = Math.max(90, Math.min(width, height) * 0.35);

    nodes.forEach((n, i) => {
        const angle = (i / count) * Math.PI * 2;
        n.x = cx + Math.cos(angle) * orbitRadius;
        n.y = cy + Math.sin(angle) * orbitRadius;
        n.vx = 0;
        n.vy = 0;
        n.radius = n.radius || 17;
    });

    graphState.zoom = 1;
    graphState.panX = 0;
    graphState.panY = 0;
}

async function loadGraphData() {
    try {
        const data = await api("/api/graph/data");
        const rawNodes = (data && data.nodes && data.nodes.length) ? data.nodes : FALLBACK_GRAPH_NODES;
        const rawLinks = (data && data.links && data.links.length) ? data.links : FALLBACK_GRAPH_LINKS;

        const canvas = $("graphCanvas");
        const container = $("canvasContainer") || (canvas ? canvas.parentElement : null);
        const rect = container ? container.getBoundingClientRect() : { width: 800, height: 520 };
        const width = Math.max(400, Math.floor(rect.width || (container && container.clientWidth) || 800));
        const height = Math.max(300, Math.floor(rect.height || (container && container.clientHeight) || 520));
        const cx = width / 2;
        const cy = height / 2;
        const count = rawNodes.length;
        const orbitRadius = Math.max(90, Math.min(width, height) * 0.35);

        graphState.nodes = rawNodes.map((n, i) => {
            const angle = (i / Math.max(1, count)) * Math.PI * 2;
            return {
                ...n,
                x: cx + Math.cos(angle) * orbitRadius,
                y: cy + Math.sin(angle) * orbitRadius,
                vx: 0,
                vy: 0,
                radius: 17
            };
        });

        graphState.links = rawLinks;
        graphState.highlightedNodeIds = null;
        graphState.highlightedLinkKeys = null;
        graphState.zoom = 1;
        graphState.panX = 0;
        graphState.panY = 0;

        resizeGraphCanvas();
        startSimulation();
    } catch (e) {
        console.warn("Using fallback graph data:", e);
        if (!graphState.nodes || !graphState.nodes.length) {
            graphState.nodes = FALLBACK_GRAPH_NODES.map((n, i) => {
                const angle = (i / FALLBACK_GRAPH_NODES.length) * Math.PI * 2;
                return { ...n, x: 400 + Math.cos(angle) * 160, y: 260 + Math.sin(angle) * 160, vx: 0, vy: 0, radius: 17 };
            });
            graphState.links = [...FALLBACK_GRAPH_LINKS];
        }
        startSimulation();
    }
}

function setupGraphCanvas() {
    const canvas = $("graphCanvas");
    if (!canvas) return;

    resizeGraphCanvas();
    window.addEventListener("resize", resizeGraphCanvas);

    canvas.addEventListener("mousedown", onCanvasMouseDown);
    canvas.addEventListener("mousemove", onCanvasMouseMove);
    window.addEventListener("mouseup", onCanvasMouseUp);
    canvas.addEventListener("wheel", onCanvasWheel, { passive: false });
}

function getCanvasMousePos(e) {
    const canvas = $("graphCanvas");
    const rect = canvas ? canvas.getBoundingClientRect() : { left: 0, top: 0, width: 800, height: 520 };
    const clientX = e.clientX || 0;
    const clientY = e.clientY || 0;
    const width = rect.width || 800;
    const height = rect.height || 520;

    const screenX = clientX - rect.left;
    const screenY = clientY - rect.top;

    const zoom = Number(graphState.zoom) || 1;
    const panX = Number(graphState.panX) || 0;
    const panY = Number(graphState.panY) || 0;

    const worldX = (screenX - width / 2 - panX) / zoom + width / 2;
    const worldY = (screenY - height / 2 - panY) / zoom + height / 2;

    return { screenX, screenY, worldX, worldY, clientX, clientY };
}

function findNodeAt(worldX, worldY) {
    const visibleNodes = getVisibleNodes();
    for (let i = visibleNodes.length - 1; i >= 0; i--) {
        const n = visibleNodes[i];
        const dx = worldX - n.x;
        const dy = worldY - n.y;
        const r = Number(n.radius) || 17;
        if (Math.sqrt(dx * dx + dy * dy) <= r + 6) {
            return n;
        }
    }
    return null;
}

function getVisibleNodes() {
    if (!graphState.nodes || !graphState.nodes.length) {
        return FALLBACK_GRAPH_NODES;
    }
    const filter = graphState.filter || "ALL";
    if (filter === "ALL") return graphState.nodes;

    const filtered = graphState.nodes.filter(n => {
        const type = String(n.type || "").toLowerCase();
        if (filter === "Product") return type.includes("product");
        if (filter === "Driver") return type.includes("driver") || type.includes("order");
        if (filter === "Supplier") return type.includes("supplier") || type.includes("product");
        if (filter === "Warehouse") return type.includes("warehouse") || type.includes("product");
        if (filter === "Order") return type.includes("order") || type.includes("product");
        if (filter === "Customer") return type.includes("customer") || type.includes("order");
        return type === filter.toLowerCase();
    });

    return filtered.length > 0 ? filtered : graphState.nodes;
}

function onCanvasMouseDown(e) {
    const pos = getCanvasMousePos(e);
    const node = findNodeAt(pos.worldX, pos.worldY);

    if (node) {
        graphState.dragNode = node;
        selectNode(node);
    } else {
        graphState.isDragging = true;
        graphState.lastMouseX = pos.screenX;
        graphState.lastMouseY = pos.screenY;
    }
}

function onCanvasMouseMove(e) {
    const pos = getCanvasMousePos(e);

    if (graphState.dragNode) {
        graphState.dragNode.x = pos.worldX;
        graphState.dragNode.y = pos.worldY;
        graphState.dragNode.vx = 0;
        graphState.dragNode.vy = 0;
    } else if (graphState.isDragging) {
        const dx = pos.screenX - graphState.lastMouseX;
        const dy = pos.screenY - graphState.lastMouseY;
        graphState.panX += dx;
        graphState.panY += dy;
        graphState.lastMouseX = pos.screenX;
        graphState.lastMouseY = pos.screenY;
    } else {
        const node = findNodeAt(pos.worldX, pos.worldY);
        graphState.hoveredNode = node;
        const tooltip = $("graphTooltip");
        if (node && tooltip) {
            tooltip.innerHTML = `<strong>${escapeHtml(node.label || node.id)}</strong><br/><small style="color:${getNodeColor(node.type)}">${escapeHtml(node.type)}</small>`;
            tooltip.style.left = (pos.screenX + 15) + "px";
            tooltip.style.top = (pos.screenY + 15) + "px";
            tooltip.classList.remove("hidden");
        } else if (tooltip) {
            tooltip.classList.add("hidden");
        }
    }
}

function onCanvasMouseUp() {
    graphState.dragNode = null;
    graphState.isDragging = false;
}

function onCanvasWheel(e) {
    e.preventDefault();
    const factor = e.deltaY < 0 ? 1.1 : 0.9;
    graphState.zoom = Math.max(0.3, Math.min(3.0, (Number(graphState.zoom) || 1) * factor));
}

function selectNode(node) {
    graphState.selectedNode = node;
    const drawer = $("nodeDetailDrawer");
    if (!drawer) return;

    $("drawerNodeTitle").textContent = node.label || node.id;
    const body = $("drawerNodeBody");

    const props = node.properties || {};
    let html = `
        <div class="drawer-prop-row">
            <div class="drawer-prop-key">Type</div>
            <div class="drawer-prop-val"><span class="badge" style="background:${getNodeColor(node.type)}33; color:${getNodeColor(node.type)}">${escapeHtml(node.type)}</span></div>
        </div>
        <div class="drawer-prop-row">
            <div class="drawer-prop-key">ID</div>
            <div class="drawer-prop-val"><code>${escapeHtml(node.id)}</code></div>
        </div>
    `;

    for (const [key, val] of Object.entries(props)) {
        html += `
            <div class="drawer-prop-row">
                <div class="drawer-prop-key">${escapeHtml(key)}</div>
                <div class="drawer-prop-val">${escapeHtml(String(val))}</div>
            </div>
        `;
    }

    const connectedLinks = graphState.links.filter(l => l.source === node.id || l.target === node.id);
    html += `<h5 style="margin:14px 0 8px 0; color:#94a3b8; font-size:0.75rem; text-transform:uppercase;">Connected Edges (${connectedLinks.length})</h5>`;
    html += `<ul style="padding-left:14px; font-size:0.8rem; margin:0; color:#cbd5e1;">`;
    connectedLinks.forEach(l => {
        const isOut = l.source === node.id;
        html += `<li><strong>${isOut ? "&rarr;" : "&larr;"} ${escapeHtml(l.label || "EDGE")}</strong>: <code>${escapeHtml(isOut ? l.target : l.source)}</code></li>`;
    });
    html += `</ul>`;

    body.innerHTML = html;
    drawer.classList.remove("hidden");
}

function startSimulation() {
    const canvas = $("graphCanvas");
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    if (animationFrameId) {
        cancelAnimationFrame(animationFrameId);
        animationFrameId = null;
    }

    function tick() {
        try {
            const container = $("canvasContainer") || canvas.parentElement;
            const rect = container ? container.getBoundingClientRect() : { width: 800, height: 520 };
            const width = Math.max(400, Math.floor(rect.width || (container && container.clientWidth) || 800));
            const height = Math.max(300, Math.floor(rect.height || (container && container.clientHeight) || 520));
            const cx = width / 2;
            const cy = height / 2;
            const dpr = window.devicePixelRatio || 1;

            // Ensure canvas resolution accurately matches backing store
            const targetPixelWidth = Math.floor(width * dpr);
            const targetPixelHeight = Math.floor(height * dpr);
            if (canvas.width !== targetPixelWidth || canvas.height !== targetPixelHeight) {
                canvas.width = targetPixelWidth;
                canvas.height = targetPixelHeight;
                canvas.style.width = width + "px";
                canvas.style.height = height + "px";
            }

            // Always reset transform, clear backing canvas, and scale by DPR
            ctx.setTransform(1, 0, 0, 1, 0, 0);
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

            // Subtle dark grid background to guarantee canvas is active and textured
            ctx.strokeStyle = "rgba(30, 41, 59, 0.4)";
            ctx.lineWidth = 1;
            const gridSize = 40;
            ctx.beginPath();
            for (let gx = 0; gx < width; gx += gridSize) {
                ctx.moveTo(gx, 0);
                ctx.lineTo(gx, height);
            }
            for (let gy = 0; gy < height; gy += gridSize) {
                ctx.moveTo(0, gy);
                ctx.lineTo(width, gy);
            }
            ctx.stroke();

            const nodes = getVisibleNodes();
            if (!nodes || nodes.length === 0) {
                ctx.fillStyle = "#94a3b8";
                ctx.font = "14px 'Segoe UI', sans-serif";
                ctx.textAlign = "center";
                ctx.textBaseline = "middle";
                ctx.fillText("No entities match this filter. Select 'All Entities (Full Graph)'.", cx, cy);
                return;
            }

            // Guarantee valid node coordinates
            nodes.forEach((n, i) => {
                if (!isFinite(n.x) || !isFinite(n.y) || n.x === null || n.y === null) {
                    const angle = (i / Math.max(1, nodes.length)) * Math.PI * 2;
                    n.x = cx + Math.cos(angle) * 140;
                    n.y = cy + Math.sin(angle) * 140;
                    n.vx = 0;
                    n.vy = 0;
                }
                if (!n.radius || !isFinite(n.radius)) {
                    n.radius = 17;
                }
            });

            const nodeMap = new Map(nodes.map(n => [n.id, n]));

            // Physics step with strict stability & bounds
            if (graphState.animating && nodes.length > 0) {
                // 1. Centering pull
                nodes.forEach(n => {
                    if (n !== graphState.dragNode) {
                        n.vx += (cx - n.x) * 0.012;
                        n.vy += (cy - n.y) * 0.012;
                    }
                });

                // 2. Node-node repulsion with safe minimum distance clamp
                for (let i = 0; i < nodes.length; i++) {
                    for (let j = i + 1; j < nodes.length; j++) {
                        const n1 = nodes[i];
                        const n2 = nodes[j];
                        let dx = n1.x - n2.x;
                        let dy = n1.y - n2.y;
                        let dist = Math.sqrt(dx * dx + dy * dy);
                        if (dist < 1) {
                            dx = (Math.random() - 0.5) * 6;
                            dy = (Math.random() - 0.5) * 6;
                            dist = Math.sqrt(dx * dx + dy * dy) || 1;
                        }
                        if (dist < 220) {
                            const rep = Math.min(2.0, (220 - dist) / dist * 0.08);
                            n1.vx += dx * rep;
                            n1.vy += dy * rep;
                            n2.vx -= dx * rep;
                            n2.vy -= dy * rep;
                        }
                    }
                }

                // 3. Link spring force
                graphState.links.forEach(l => {
                    const s = nodeMap.get(l.source);
                    const t = nodeMap.get(l.target);
                    if (s && t) {
                        const dx = t.x - s.x;
                        const dy = t.y - s.y;
                        const dist = Math.sqrt(dx * dx + dy * dy) || 1;
                        const spring = Math.min(2.0, (dist - 110) * 0.02);
                        s.vx += dx * spring;
                        s.vy += dy * spring;
                        t.vx -= dx * spring;
                        t.vy -= dy * spring;
                    }
                });

                // 4. Velocity cap, friction damping, and canvas boundary clamping
                const pad = 35;
                nodes.forEach(n => {
                    if (n !== graphState.dragNode) {
                        const maxV = 6;
                        n.vx = Math.max(-maxV, Math.min(maxV, n.vx));
                        n.vy = Math.max(-maxV, Math.min(maxV, n.vy));

                        n.x += n.vx;
                        n.y += n.vy;

                        n.vx *= 0.85;
                        n.vy *= 0.85;

                        n.x = Math.max(pad, Math.min(width - pad, n.x));
                        n.y = Math.max(pad, Math.min(height - pad, n.y));
                    }
                });
            }

            // Apply Pan & Zoom Transform
            ctx.save();
            const zoom = Math.max(0.2, Math.min(3.5, Number(graphState.zoom) || 1));
            const panX = Number(graphState.panX) || 0;
            const panY = Number(graphState.panY) || 0;

            ctx.translate(width / 2 + panX, height / 2 + panY);
            ctx.scale(zoom, zoom);
            ctx.translate(-width / 2, -height / 2);

            // Draw Links
            graphState.links.forEach(l => {
                const s = nodeMap.get(l.source);
                const t = nodeMap.get(l.target);
                if (!s || !t) return;

                const isHighlighted = graphState.highlightedLinkKeys && graphState.highlightedLinkKeys.has(l.source + "->" + l.target);
                const isDimmed = graphState.highlightedNodeIds && (!graphState.highlightedNodeIds.has(s.id) || !graphState.highlightedNodeIds.has(t.id));

                ctx.beginPath();
                ctx.moveTo(s.x, s.y);
                ctx.lineTo(t.x, t.y);
                ctx.lineWidth = isHighlighted ? 3.5 : 1.5;
                ctx.strokeStyle = isHighlighted ? "#f59e0b" : (isDimmed ? "rgba(51, 65, 85, 0.25)" : "#334155");
                ctx.stroke();

                // Midpoint label
                if (!isDimmed || isHighlighted) {
                    const mx = (s.x + t.x) / 2;
                    const my = (s.y + t.y) / 2;
                    ctx.fillStyle = isHighlighted ? "#fbbf24" : "#64748b";
                    ctx.font = "9px 'JetBrains Mono', monospace";
                    ctx.textAlign = "center";
                    ctx.textBaseline = "middle";
                    ctx.fillText(l.label || "", mx, my - 5);
                }
            });

            // Draw Nodes
            nodes.forEach(n => {
                const isHighlighted = graphState.highlightedNodeIds && (
                    graphState.highlightedNodeIds.has(n.id) ||
                    (n.properties && graphState.highlightedNodeIds.has(String(n.properties.id)))
                );
                const isDimmed = graphState.highlightedNodeIds && !isHighlighted;
                const isHovered = n === graphState.hoveredNode;
                const color = getNodeColor(n.type);

                ctx.save();
                if (isDimmed) ctx.globalAlpha = 0.3;

                // Outer ring
                if (isHighlighted || isHovered) {
                    ctx.beginPath();
                    ctx.arc(n.x, n.y, n.radius + 7, 0, Math.PI * 2);
                    ctx.fillStyle = isHighlighted ? "rgba(245, 158, 11, 0.25)" : "rgba(255, 255, 255, 0.15)";
                    ctx.fill();
                    ctx.lineWidth = 2.5;
                    ctx.strokeStyle = isHighlighted ? "#f59e0b" : "#fff";
                    ctx.stroke();
                }

                // Node Circle
                ctx.beginPath();
                ctx.arc(n.x, n.y, n.radius, 0, Math.PI * 2);
                ctx.fillStyle = color;
                ctx.fill();
                ctx.lineWidth = 2;
                ctx.strokeStyle = "#0f172a";
                ctx.stroke();

                // Letter Icon
                ctx.fillStyle = "#ffffff";
                ctx.font = "bold 11px sans-serif";
                ctx.textAlign = "center";
                ctx.textBaseline = "middle";
                ctx.fillText(getNodeLetter(n.type), n.x, n.y);

                // Label Below
                ctx.fillStyle = isHighlighted ? "#fcd34d" : "#e2e8f0";
                ctx.font = isHighlighted ? "bold 11px sans-serif" : "11px sans-serif";
                ctx.textAlign = "center";
                ctx.fillText(n.label || n.id, n.x, n.y + n.radius + 14);

                ctx.restore();
            });

            ctx.restore();
        } catch (err) {
            console.error("Graph animation tick error:", err);
        } finally {
            animationFrameId = requestAnimationFrame(tick);
        }
    }

    animationFrameId = requestAnimationFrame(tick);
}

function setupGraphEventListeners() {
    const filterEl = $("graphFilter");
    if (filterEl) {
        filterEl.addEventListener("change", e => {
            graphState.filter = e.target.value;
            layoutVisibleNodes();
        });
    }

    if ($("btnZoomIn")) {
        $("btnZoomIn").addEventListener("click", () => {
            graphState.zoom = Math.min(3.0, (Number(graphState.zoom) || 1) * 1.2);
        });
    }

    if ($("btnZoomOut")) {
        $("btnZoomOut").addEventListener("click", () => {
            graphState.zoom = Math.max(0.3, (Number(graphState.zoom) || 1) * 0.8);
        });
    }

    if ($("btnResetZoom")) {
        $("btnResetZoom").addEventListener("click", () => {
            layoutVisibleNodes();
            showToast("Centered graph view.");
        });
    }

    async function executeTrace() {
        const supId = $("traceSupplierSelect") ? $("traceSupplierSelect").value : "SUPPLIER_S01";
        try {
            const data = await api(`/api/graph/trace?supplierId=${encodeURIComponent(supId)}`);
            const pathNodes = data.nodes || [];
            const pathLinks = data.links || [];

            const idSet = new Set();
            pathNodes.forEach(n => {
                idSet.add(n.id);
                if (n.properties && n.properties.id) {
                    idSet.add(String(n.properties.id));
                    idSet.add("SUPPLIER_" + n.properties.id);
                    idSet.add("PRODUCT_" + n.properties.id);
                    idSet.add("WAREHOUSE_" + n.properties.id);
                    idSet.add("CUSTOMER_" + n.properties.id);
                    idSet.add("ORDER_" + n.properties.id);
                    idSet.add("DRIVER_" + n.properties.id);
                }
            });

            const linkSet = new Set();
            pathLinks.forEach(l => {
                linkSet.add(l.source + "->" + l.target);
            });

            graphState.highlightedNodeIds = idSet;
            graphState.highlightedLinkKeys = linkSet;
            graphState.filter = "ALL";
            if ($("graphFilter")) $("graphFilter").value = "ALL";

            showToast(`Supply chain trace completed across ${pathNodes.length} nodes!`);
        } catch (e) {
            showToast("Trace failed: " + e.message);
        }
    }

    if ($("btnTracePath")) {
        $("btnTracePath").addEventListener("click", executeTrace);
    }

    if ($("traceSupplierSelect")) {
        $("traceSupplierSelect").addEventListener("change", executeTrace);
    }

    if ($("btnResetTrace")) {
        $("btnResetTrace").addEventListener("click", () => {
            graphState.highlightedNodeIds = null;
            graphState.highlightedLinkKeys = null;
            showToast("Path highlight reset.");
        });
    }

    if ($("closeDrawer")) {
        $("closeDrawer").addEventListener("click", () => {
            $("nodeDetailDrawer").classList.add("hidden");
        });
    }

    if ($("refreshGraph")) {
        $("refreshGraph").addEventListener("click", () => {
            loadGraphData();
            loadGraphStatus();
            showToast("Refreshed graph network.");
        });
    }

    // Cypher shortcut pills
    document.querySelectorAll(".pill-btn").forEach(btn => {
        btn.addEventListener("click", () => {
            const q = btn.dataset.cypher;
            if (q) {
                $("cypherInput").value = q;
                runCypherQuery();
            }
        });
    });

    if ($("runCypher")) {
        $("runCypher").addEventListener("click", runCypherQuery);
    }

    if ($("btnRefreshBottlenecks")) {
        $("btnRefreshBottlenecks").addEventListener("click", loadBottlenecks);
    }
}

async function runCypherQuery() {
    const q = $("cypherInput").value.trim();
    if (!q) {
        showToast("Enter a Cypher query.");
        return;
    }

    const wrap = $("cypherResultWrap");
    wrap.innerHTML = `<div class="empty-state"><div class="empty-icon">⏳</div><h3>Executing Cypher...</h3></div>`;

    try {
        const res = await api("/api/graph/cypher", {
            method: "POST",
            body: JSON.stringify({ query: q })
        });

        const rows = res.rows || [];
        const cols = res.columns || (rows.length ? Object.keys(rows[0]) : []);

        if (!rows.length) {
            wrap.innerHTML = `<div class="empty-state"><h3>No records returned (${res.durationMs}ms)</h3><p>Query completed successfully with zero matching records.</p></div>`;
            return;
        }

        let tableHtml = `
            <div style="padding:10px 14px; font-size:0.8rem; color:#94a3b8; border-bottom:1px solid #1e293b; display:flex; justify-content:space-between;">
                <span>${rows.length} records returned &middot; ${res.source || "Cypher Engine"}</span>
                <span style="color:#10b981;">Execution: ${res.durationMs}ms</span>
            </div>
            <table class="result-table">
                <thead>
                    <tr>${cols.map(c => `<th>${escapeHtml(c)}</th>`).join("")}</tr>
                </thead>
                <tbody>
                    ${rows.map(r => `
                        <tr>
                            ${cols.map(c => `<td>${escapeHtml(String(r[c] !== null && r[c] !== undefined ? (typeof r[c] === "object" ? JSON.stringify(r[c]) : r[c]) : "NULL"))}</td>`).join("")}
                        </tr>
                    `).join("")}
                </tbody>
            </table>
        `;
        wrap.innerHTML = tableHtml;
    } catch (e) {
        wrap.innerHTML = `<div class="affected"><strong>Cypher Execution Error</strong>${escapeHtml(e.message)}</div>`;
    }
}

async function loadBottlenecks() {
    try {
        const data = await api("/api/graph/bottlenecks");

        // Single Source
        const singleSourceList = $("singleSourceList");
        if (singleSourceList) {
            const risks = data.singleSourceVulnerabilities || [];
            if (!risks.length) {
                singleSourceList.innerHTML = `<p style="color:#10b981; font-size:0.85rem;">No critical single-source risks detected.</p>`;
            } else {
                singleSourceList.innerHTML = risks.map(r => `
                    <div class="risk-item">
                        <div>
                            <strong>${escapeHtml(r.productName)}</strong>
                            <div style="font-size:0.75rem; color:#64748b;">Sole Supplier: ${escapeHtml(r.soleSupplier)}</div>
                        </div>
                        <span class="risk-tag">HIGH RISK</span>
                    </div>
                `).join("");
            }
        }

        // Driver Workload
        const driverLoadList = $("driverLoadList");
        if (driverLoadList) {
            const workloads = data.driverWorkloadCentrality || {};
            driverLoadList.innerHTML = Object.entries(workloads).map(([dId, count]) => `
                <div class="risk-item">
                    <span>Driver <code>${escapeHtml(dId)}</code></span>
                    <span class="badge badge-blue">${count} Assigned Orders</span>
                </div>
            `).join("");
        }

        // Topology
        const topologyMetrics = $("topologyMetrics");
        if (topologyMetrics) {
            topologyMetrics.innerHTML = `
                <div class="risk-item"><span>Total Nodes</span><strong>${data.totalNodesAnalyzed}</strong></div>
                <div class="risk-item"><span>Total Directed Edges</span><strong>${data.totalRelationshipsAnalyzed}</strong></div>
                <div class="risk-item"><span>Graph Density</span><strong>${data.graphDensity}</strong></div>
            `;
        }
    } catch (ignored) {}
}


/* =====================================================
   ER & ENHANCED ER (EER) STUDIO
===================================================== */

const EER_MERMAID_CODE = `classDiagram
    direction TB

    class PRODUCT {
        <<Superclass>>
        +String PRODUCTID
        +String PNAME
        +String CATEGORY
        +Decimal UNITPRICE
    }
    class PERISHABLE_PRODUCT {
        <<Subclass>>
        +Integer SHELFLIFEDAYS
        +String STORAGETEMPC
    }
    class HAZARDOUS_PRODUCT {
        <<Subclass>>
        +String HAZARDCLASS
        +String HANDLINGNOTE
    }
    PRODUCT <|-- PERISHABLE_PRODUCT : Disjoint
    PRODUCT <|-- HAZARDOUS_PRODUCT : Disjoint

    class EMPLOYEE {
        <<Superclass>>
        +String EMPID
        +String ENAME
        +String ROLE
        +Decimal SALARY
        +String SUPERVISORID
    }
    class MANAGER {
        <<Subclass>>
        +String LEVELNO
        +Decimal BONUS
    }
    class DRIVER {
        <<Subclass>>
        +String LICENSENO
        +Date EXPIRY
    }
    EMPLOYEE <|-- MANAGER : Disjoint
    EMPLOYEE <|-- DRIVER : Disjoint

    class GOODS_MOVEMENT {
        <<Superclass>>
        +String MOVEMENTID
        +String ORDERID
        +String WAREHOUSEID
        +Date MOVEDATE
    }
    class SHIPMENT {
        <<Subclass>>
        +Date DELIVERYDATE
    }
    class GOODS_RETURN {
        <<Subclass>>
        +String REASON
        +Decimal REFUNDAMT
    }
    GOODS_MOVEMENT <|-- SHIPMENT : Disjoint
    GOODS_MOVEMENT <|-- GOODS_RETURN : Disjoint

    class CUSTOMER {
        +String CUSTID
        +String CNAME
        +String CITY
    }
    class ORDERS {
        +String ORDERID
        +Date ORDERDATE
        +String STATUS
    }
    class ORDER_ITEM {
        <<Weak Entity>>
        +String ORDERID
        +String PRODUCTID
        +Integer QTY
        +Decimal PRICE
    }
    class WAREHOUSE {
        +String WAREHOUSEID
        +String WNAME
        +String CITY
        +Integer CAPACITY
    }
    class SUPPLIER {
        +String SUPPLIERID
        +String SNAME
        +String CITY
    }

    CUSTOMER --> ORDERS : places
    ORDERS *-- ORDER_ITEM : contains
    ORDER_ITEM --> PRODUCT : references
    SUPPLIER --> PRODUCT : supplies
    ORDERS --> GOODS_MOVEMENT : triggers
    WAREHOUSE --> GOODS_MOVEMENT : fulfills
`;

const RELATIONAL_ER_MERMAID_CODE = `erDiagram
    CITY ||--o{ SUPPLIER : "located_in"
    CITY ||--o{ CUSTOMER : "resides_in"
    CITY ||--o{ WAREHOUSE : "located_in"
    CITY ||--o{ DRIVER_CITY : "transit_route"
    CITY ||--o{ DELIVERY_DUTY : "delivery_city"

    SUPPLIER ||--|{ SUPPLIER_PHONE : "has"
    SUPPLIER ||--o{ SUPPLIES : "supplies"
    SUPPLIER ||--o{ INSPECTION : "inspected"

    PRODUCT ||--o{ SUPPLIES : "supplied_by"
    PRODUCT ||--o{ INSPECTION : "tested_in"
    PRODUCT ||--o{ STOCK : "stored_in"
    PRODUCT ||--o| PERISHABLE_PRODUCT : "specializes"
    PRODUCT ||--o| HAZARDOUS_PRODUCT : "specializes"
    PRODUCT ||--o{ ORDER_ITEM : "ordered_in"

    WAREHOUSE ||--o{ STOCK : "holds"
    WAREHOUSE ||--o{ EMPLOYEE : "employs"
    WAREHOUSE ||--o{ GOODS_MOVEMENT : "originates"
    WAREHOUSE ||--o| MANAGER : "managed_by"

    EMPLOYEE ||--o| MANAGER : "is_a"
    EMPLOYEE ||--o| DRIVER : "is_a"
    EMPLOYEE ||--o{ EMPLOYEE : "supervises"

    DRIVER ||--o{ DRIVER_CITY : "covers"
    DRIVER ||--o{ ORDER_DRIVER : "assigned"
    DRIVER ||--o{ DELIVERY_DUTY : "delivers"

    CUSTOMER ||--o{ ORDERS : "places"

    ORDERS ||--|{ ORDER_ITEM : "contains"
    ORDERS ||--o{ ORDER_DRIVER : "dispatched_to"
    ORDERS ||--o{ DELIVERY_DUTY : "routed"
    ORDERS ||--o{ GOODS_MOVEMENT : "triggers"

    GOODS_MOVEMENT ||--o| SHIPMENT : "completes"
    GOODS_MOVEMENT ||--o| GOODS_RETURN : "reverses"

    CITY {
        string CITY PK
        string STATE
    }
    SUPPLIER {
        string SUPPLIERID PK
        string SNAME
        string CITY FK
    }
    SUPPLIER_PHONE {
        string SUPPLIERID PK
        string PHONE PK
    }
    PRODUCT {
        string PRODUCTID PK
        string PNAME
        string CATEGORY
        number UNITPRICE
    }
    PERISHABLE_PRODUCT {
        string PRODUCTID PK
        int SHELFLIFEDAYS
        string STORAGETEMPC
    }
    HAZARDOUS_PRODUCT {
        string PRODUCTID PK
        string HAZARDCLASS
        string HANDLINGNOTE
    }
    WAREHOUSE {
        string WAREHOUSEID PK
        string WNAME
        string CITY FK
        int CAPACITY
    }
    CUSTOMER {
        string CUSTID PK
        string CNAME
        string CITY FK
        string PHONE
    }
    ORDERS {
        string ORDERID PK
        string CUSTID FK
        date ORDERDATE
        string STATUS
    }
    ORDER_ITEM {
        string ORDERID PK
        string PRODUCTID PK
        int QTY
        number PRICE
    }
`;

let eerStudioInitialized = false;
let eerZoom = 1.0;
let relZoom = 1.0;
let eerRendered = false;
let relRendered = false;

function applyDiagramZoom(type) {
    const isEer = type === "eer";
    const zoom = isEer ? eerZoom : relZoom;
    const target = $(isEer ? "eerDiagramImg" : "erDiagramImg") || $(isEer ? "eerMermaidSvg" : "relMermaidSvg");
    if (target) {
        target.style.transform = `scale(${zoom})`;
    }
}

async function renderEerSvgDiagram() {
    applyDiagramZoom("eer");
}

async function renderRelationalErSvgDiagram() {
    applyDiagramZoom("rel");
}

function initEerStudio() {
    if (!eerStudioInitialized) {
        eerStudioInitialized = true;

        // View toggle buttons
        if ($("btnViewEer")) {
            $("btnViewEer").addEventListener("click", () => {
                $("eerConceptualView").classList.remove("hidden");
                $("relationalErView").classList.add("hidden");
                $("btnViewEer").className = "button primary-button";
                $("btnViewRelationalEr").className = "button secondary-button";
                applyDiagramZoom("eer");
            });
        }

        if ($("btnViewRelationalEr")) {
            $("btnViewRelationalEr").addEventListener("click", () => {
                $("eerConceptualView").classList.add("hidden");
                $("relationalErView").classList.remove("hidden");
                $("btnViewEer").className = "button secondary-button";
                $("btnViewRelationalEr").className = "button primary-button";
                applyDiagramZoom("rel");
                if ($("schemaTableSelect")) {
                    populateSchemaTableSelect();
                }
            });
        }

        // Copy Mermaid code (if present)
        if ($("btnCopyMermaid")) {
            $("btnCopyMermaid").addEventListener("click", () => {
                const isEer = !$("eerConceptualView").classList.contains("hidden");
                const code = isEer ? EER_MERMAID_CODE : RELATIONAL_ER_MERMAID_CODE;
                navigator.clipboard.writeText(code).then(() => {
                    showToast("Diagram code copied to clipboard!");
                });
            });
        }

        // Zoom controls for EER
        if ($("btnZoomInEer")) {
            $("btnZoomInEer").addEventListener("click", () => {
                eerZoom = Math.min(2.5, +(eerZoom + 0.15).toFixed(2));
                applyDiagramZoom("eer");
            });
        }
        if ($("btnZoomOutEer")) {
            $("btnZoomOutEer").addEventListener("click", () => {
                eerZoom = Math.max(0.4, +(eerZoom - 0.15).toFixed(2));
                applyDiagramZoom("eer");
            });
        }
        if ($("btnResetEerZoom")) {
            $("btnResetEerZoom").addEventListener("click", () => {
                eerZoom = 1.0;
                applyDiagramZoom("eer");
            });
        }

        // Zoom controls for Relational ER
        if ($("btnZoomInRel")) {
            $("btnZoomInRel").addEventListener("click", () => {
                relZoom = Math.min(2.5, +(relZoom + 0.15).toFixed(2));
                applyDiagramZoom("rel");
            });
        }
        if ($("btnZoomOutRel")) {
            $("btnZoomOutRel").addEventListener("click", () => {
                relZoom = Math.max(0.4, +(relZoom - 0.15).toFixed(2));
                applyDiagramZoom("rel");
            });
        }
        if ($("btnResetRelZoom")) {
            $("btnResetRelZoom").addEventListener("click", () => {
                relZoom = 1.0;
                applyDiagramZoom("rel");
            });
        }

        if ($("schemaTableSelect")) {
            $("schemaTableSelect").addEventListener("change", e => {
                inspectTableSchema(e.target.value);
            });
        }
    }

    applyDiagramZoom("eer");
}

function populateSchemaTableSelect() {
    const sel = $("schemaTableSelect");
    if (!sel || sel.options.length > 1) return;

    const tables = state.tables.length ? state.tables : [
        "CITY", "SUPPLIER", "SUPPLIER_PHONE", "PRODUCT", "PERISHABLE_PRODUCT",
        "HAZARDOUS_PRODUCT", "SUPPLIES", "INSPECTION", "WAREHOUSE", "STOCK",
        "EMPLOYEE", "MANAGER", "DRIVER", "DRIVER_CITY", "CUSTOMER", "ORDERS",
        "ORDER_ITEM", "ORDER_DRIVER", "DELIVERY_DUTY", "GOODS_MOVEMENT",
        "SHIPMENT", "GOODS_RETURN"
    ];

    sel.innerHTML = `<option value="">Select a table to inspect...</option>` +
        tables.map(t => `<option value="${escapeHtml(t)}">${escapeHtml(t)}</option>`).join("");
}

async function inspectTableSchema(tableName) {
    const box = $("tableSchemaDetails");
    if (!box || !tableName) return;

    box.innerHTML = `<p style="color:#94a3b8;">Loading schema for ${escapeHtml(tableName)}...</p>`;

    try {
        const data = await api(`/api/tables/${encodeURIComponent(tableName)}/columns`);
        const cols = data.columns || [];
        const pks = new Set(data.primaryKeys || []);

        let html = `
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px;">
                <h4 style="margin:0; font-size:1rem; color:#f8fafc;">Table: <code>${escapeHtml(tableName)}</code></h4>
                <span class="badge badge-purple">${cols.length} Columns</span>
            </div>
            <table class="result-table">
                <thead>
                    <tr>
                        <th>Key</th>
                        <th>Column Name</th>
                        <th>Data Type</th>
                        <th>Length</th>
                        <th>Nullable</th>
                    </tr>
                </thead>
                <tbody>
                    ${cols.map(c => `
                        <tr>
                            <td>${pks.has(c.name) ? `<span class="badge badge-amber">PK</span>` : ""}</td>
                            <td><strong>${escapeHtml(c.name)}</strong></td>
                            <td><code>${escapeHtml(c.type)}</code></td>
                            <td>${c.length !== null ? escapeHtml(String(c.length)) : "-"}</td>
                            <td>${c.nullable ? "NULL" : "NOT NULL"}</td>
                        </tr>
                    `).join("")}
                </tbody>
            </table>
        `;
        box.innerHTML = html;
    } catch (e) {
        box.innerHTML = `<p style="color:#ef4444;">Error: ${escapeHtml(e.message)}</p>`;
    }
}


/* =====================================================
   CLOUD DEPLOYMENT & HEALTH CHECK
===================================================== */

async function checkCloudHealth() {
    if ($("cloudDbStatus")) {
        $("cloudDbStatus").textContent = "Checking...";
    }

    try {
        const health = await api("/api/health");
        const isUp = health.status === "UP";
        const dbName = health.database || "Oracle";

        if ($("cloudDbStatus")) {
            $("cloudDbStatus").innerHTML = `<span class="badge ${isUp ? "badge-green" : "badge-amber"}">${escapeHtml(dbName)} (${health.status})</span>`;
        }

        const connIndicator = document.querySelector(".connection-info small");
        if (connIndicator) {
            connIndicator.textContent = dbName;
        }
    } catch (e) {
        if ($("cloudDbStatus")) {
            $("cloudDbStatus").innerHTML = `<span class="badge badge-amber">Oracle Emulation Active</span>`;
        }
    }

    loadGraphStatus();
}

if ($("btnCheckCloudHealth")) {
    $("btnCheckCloudHealth").addEventListener("click", () => {
        checkCloudHealth();
        showToast("Checked live system health.");
    });
}