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
    } else if (page === "eer") {
        initEerStudio();
    } else if (page === "cloud") {
        checkCloudHealth();
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

    try {

        const data =
            await api(
                "/api/cursors/customers"
            );


        renderCursorResult(
            "customerCursorResult",
            data.rows,
            "No customers found."
        );


    } catch (error) {

        showToast(error.message);

    }

}


/* =====================================================
   CURSOR 2
   PARAMETERIZED ORDER CURSOR
===================================================== */

async function runOrderCursor() {

    const customerId =
        $("cursorCustomerId")
            .value
            .trim();


    if (!customerId) {

        showToast(
            "Enter a customer ID."
        );

        return;

    }


    try {

        const data =
            await api(
                `/api/cursors/orders?custId=${encodeURIComponent(
                    customerId
                )}`
            );


        renderCursorResult(
            "orderCursorResult",
            data.rows,
            `No orders found for ${customerId}.`
        );


    } catch (error) {

        showToast(error.message);

    }

}


/* =====================================================
   CURSOR 3
   ORDER TOTAL CURSOR
===================================================== */

async function runOrderTotalCursor() {

    try {

        const data =
            await api(
                "/api/cursors/order-totals"
            );


        renderCursorResult(
            "orderTotalCursorResult",
            data.rows,
            "No order totals found."
        );


    } catch (error) {

        showToast(error.message);

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

const graphState = {
    initialized: false,
    nodes: [],
    links: [],
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
    return NODE_COLORS[type] || NODE_COLORS.Default;
}

function getNodeLetter(type) {
    if (type === "Supplier") return "S";
    if (type.includes("Product")) return "P";
    if (type === "Warehouse") return "W";
    if (type === "Order") return "O";
    if (type === "Customer") return "C";
    if (type === "Driver") return "D";
    if (type === "Manager") return "M";
    return "N";
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

async function loadGraphData() {
    try {
        const data = await api("/api/graph/data");
        const rawNodes = data.nodes || [];
        const rawLinks = data.links || [];

        const canvas = $("graphCanvas");
        const width = canvas ? canvas.clientWidth : 800;
        const height = canvas ? canvas.clientHeight : 520;
        const cx = width / 2;
        const cy = height / 2;

        graphState.nodes = rawNodes.map((n, i) => {
            const angle = (i / rawNodes.length) * Math.PI * 2;
            const radius = 140 + (i % 3) * 60;
            return {
                ...n,
                x: cx + Math.cos(angle) * radius + (Math.random() - 0.5) * 40,
                y: cy + Math.sin(angle) * radius + (Math.random() - 0.5) * 40,
                vx: 0,
                vy: 0,
                radius: 17
            };
        });

        graphState.links = rawLinks;
        graphState.highlightedNodeIds = null;
        graphState.highlightedLinkKeys = null;
        startSimulation();
    } catch (e) {
        showToast("Error loading graph topology: " + e.message);
    }
}

function setupGraphCanvas() {
    const canvas = $("graphCanvas");
    if (!canvas) return;

    function resize() {
        const rect = canvas.getBoundingClientRect();
        const dpr = window.devicePixelRatio || 1;
        canvas.width = rect.width * dpr;
        canvas.height = rect.height * dpr;
        const ctx = canvas.getContext("2d");
        ctx.scale(dpr, dpr);
    }

    resize();
    window.addEventListener("resize", resize);

    canvas.addEventListener("mousedown", onCanvasMouseDown);
    canvas.addEventListener("mousemove", onCanvasMouseMove);
    window.addEventListener("mouseup", onCanvasMouseUp);
    canvas.addEventListener("wheel", onCanvasWheel, { passive: false });
}

function getCanvasMousePos(e) {
    const canvas = $("graphCanvas");
    const rect = canvas.getBoundingClientRect();
    const clientX = e.clientX;
    const clientY = e.clientY;
    const width = rect.width;
    const height = rect.height;

    const screenX = clientX - rect.left;
    const screenY = clientY - rect.top;

    const worldX = (screenX - width / 2 - graphState.panX) / graphState.zoom + width / 2;
    const worldY = (screenY - height / 2 - graphState.panY) / graphState.zoom + height / 2;

    return { screenX, screenY, worldX, worldY, clientX, clientY };
}

function findNodeAt(worldX, worldY) {
    const visibleNodes = getVisibleNodes();
    for (let i = visibleNodes.length - 1; i >= 0; i--) {
        const n = visibleNodes[i];
        const dx = worldX - n.x;
        const dy = worldY - n.y;
        if (Math.sqrt(dx * dx + dy * dy) <= n.radius + 6) {
            return n;
        }
    }
    return null;
}

function getVisibleNodes() {
    if (graphState.filter === "ALL") return graphState.nodes;
    return graphState.nodes.filter(n => {
        if (graphState.filter === "Product") return String(n.type).includes("Product");
        return n.type === graphState.filter;
    });
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
            tooltip.innerHTML = `<strong>${escapeHtml(node.label)}</strong><br/><small style="color:${getNodeColor(node.type)}">${escapeHtml(node.type)}</small>`;
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
    graphState.zoom = Math.max(0.3, Math.min(3.0, graphState.zoom * factor));
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
        html += `<li><strong>${isOut ? "&rarr;" : "&larr;"} ${escapeHtml(l.label)}</strong>: <code>${escapeHtml(isOut ? l.target : l.source)}</code></li>`;
    });
    html += `</ul>`;

    body.innerHTML = html;
    drawer.classList.remove("hidden");
}

function startSimulation() {
    const canvas = $("graphCanvas");
    if (!canvas) return;
    const ctx = canvas.getContext("2d");

    function tick() {
        const rect = canvas.getBoundingClientRect();
        const width = rect.width;
        const height = rect.height;
        const cx = width / 2;
        const cy = height / 2;
        const nodes = getVisibleNodes();
        const nodeMap = new Map(nodes.map(n => [n.id, n]));

        // Physics step
        if (graphState.animating) {
            for (let i = 0; i < nodes.length; i++) {
                for (let j = i + 1; j < nodes.length; j++) {
                    const n1 = nodes[i];
                    const n2 = nodes[j];
                    const dx = n1.x - n2.x;
                    const dy = n1.y - n2.y;
                    const dist = Math.sqrt(dx * dx + dy * dy) || 1;
                    if (dist < 260) {
                        const rep = (260 - dist) / dist * 0.12;
                        n1.vx += dx * rep;
                        n1.vy += dy * rep;
                        n2.vx -= dx * rep;
                        n2.vy -= dy * rep;
                    }
                }
            }

            graphState.links.forEach(l => {
                const s = nodeMap.get(l.source);
                const t = nodeMap.get(l.target);
                if (s && t) {
                    const dx = t.x - s.x;
                    const dy = t.y - s.y;
                    const dist = Math.sqrt(dx * dx + dy * dy) || 1;
                    const spring = (dist - 110) * 0.02;
                    s.vx += dx * spring;
                    s.vy += dy * spring;
                    t.vx -= dx * spring;
                    t.vy -= dy * spring;
                }
            });

            nodes.forEach(n => {
                if (n !== graphState.dragNode) {
                    n.vx += (cx - n.x) * 0.008;
                    n.vy += (cy - n.y) * 0.008;
                    n.x += n.vx;
                    n.y += n.vy;
                    n.vx *= 0.82;
                    n.vy *= 0.82;
                }
            });
        }

        // Render step
        ctx.clearRect(0, 0, width, height);
        ctx.save();
        ctx.translate(width / 2 + graphState.panX, height / 2 + graphState.panY);
        ctx.scale(graphState.zoom, graphState.zoom);
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
            ctx.lineWidth = isHighlighted ? 3 : 1.5;
            ctx.strokeStyle = isHighlighted ? "#f59e0b" : (isDimmed ? "rgba(51, 65, 85, 0.25)" : "#334155");
            ctx.stroke();

            // Midpoint label
            if (!isDimmed) {
                const mx = (s.x + t.x) / 2;
                const my = (s.y + t.y) / 2;
                ctx.fillStyle = isHighlighted ? "#fbbf24" : "#64748b";
                ctx.font = "9px 'JetBrains Mono', monospace";
                ctx.textAlign = "center";
                ctx.textBaseline = "middle";
                ctx.fillText(l.label, mx, my - 5);
            }
        });

        // Draw Nodes
        nodes.forEach(n => {
            const isHighlighted = graphState.highlightedNodeIds && graphState.highlightedNodeIds.has(n.id);
            const isDimmed = graphState.highlightedNodeIds && !isHighlighted;
            const isHovered = n === graphState.hoveredNode;
            const color = getNodeColor(n.type);

            ctx.save();
            if (isDimmed) ctx.globalAlpha = 0.25;

            // Outer ring
            if (isHighlighted || isHovered) {
                ctx.beginPath();
                ctx.arc(n.x, n.y, n.radius + 6, 0, Math.PI * 2);
                ctx.fillStyle = isHighlighted ? "rgba(245, 158, 11, 0.2)" : "rgba(255, 255, 255, 0.15)";
                ctx.fill();
                ctx.lineWidth = 2;
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
            ctx.font = "11px sans-serif";
            ctx.textAlign = "center";
            ctx.fillText(n.label || n.id, n.x, n.y + n.radius + 14);

            ctx.restore();
        });

        ctx.restore();
        requestAnimationFrame(tick);
    }

    requestAnimationFrame(tick);
}

function setupGraphEventListeners() {
    $("graphFilter").addEventListener("change", e => {
        graphState.filter = e.target.value;
    });

    $("btnZoomIn").addEventListener("click", () => {
        graphState.zoom = Math.min(3.0, graphState.zoom * 1.2);
    });

    $("btnZoomOut").addEventListener("click", () => {
        graphState.zoom = Math.max(0.3, graphState.zoom * 0.8);
    });

    $("btnResetZoom").addEventListener("click", () => {
        graphState.zoom = 1;
        graphState.panX = 0;
        graphState.panY = 0;
    });

    $("btnTracePath").addEventListener("click", async () => {
        const supId = $("traceSupplierSelect").value;
        try {
            const data = await api(`/api/graph/trace?supplierId=${encodeURIComponent(supId)}`);
            const pathNodes = data.nodes || [];
            const pathLinks = data.links || [];
            graphState.highlightedNodeIds = new Set(pathNodes.map(n => n.id));
            graphState.highlightedLinkKeys = new Set(pathLinks.map(l => l.source + "->" + l.target));
            showToast(`Supply Chain trace completed across ${pathNodes.length} nodes and ${data.hops} hops!`);
        } catch (e) {
            showToast("Trace failed: " + e.message);
        }
    });

    $("btnResetTrace").addEventListener("click", () => {
        graphState.highlightedNodeIds = null;
        graphState.highlightedLinkKeys = null;
        showToast("Path highlight reset.");
    });

    $("closeDrawer").addEventListener("click", () => {
        $("nodeDetailDrawer").classList.add("hidden");
    });

    $("refreshGraph").addEventListener("click", () => {
        loadGraphData();
        loadGraphStatus();
        showToast("Refreshed graph network.");
    });

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

    $("runCypher").addEventListener("click", runCypherQuery);

    $("btnRefreshBottlenecks").addEventListener("click", loadBottlenecks);



    $("openJustificationModal").addEventListener("click", () => {
        $("justificationModal").classList.remove("hidden");
    });

    $("closeJustificationModal").addEventListener("click", () => {
        $("justificationModal").classList.add("hidden");
    });

    $("closeJustificationBtn").addEventListener("click", () => {
        $("justificationModal").classList.add("hidden");
    });
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
    const target = $(isEer ? "eerMermaidSvg" : "relMermaidSvg");
    if (target) {
        target.style.transform = `scale(${zoom})`;
    }
}

async function renderEerSvgDiagram() {
    const container = $("eerMermaidSvg");
    if (!container || eerRendered) return;
    try {
        if (window.mermaid) {
            container.innerHTML = `<div style="color:#94a3b8; padding:20px; font-size:0.9rem;">Rendering EER Conceptual Model...</div>`;
            const id = "eerSvg_" + Math.floor(Math.random() * 1000000);
            const { svg } = await mermaid.render(id, EER_MERMAID_CODE);
            container.innerHTML = svg;
            eerRendered = true;
            applyDiagramZoom("eer");
        }
    } catch (err) {
        console.error("Mermaid EER render error:", err);
        container.innerHTML = `<div style="color:#f87171; padding:20px;">Could not render diagram: ${escapeHtml(err.message || String(err))}</div>`;
    }
}

async function renderRelationalErSvgDiagram() {
    const container = $("relMermaidSvg");
    if (!container || relRendered) return;
    try {
        if (window.mermaid) {
            container.innerHTML = `<div style="color:#94a3b8; padding:20px; font-size:0.9rem;">Rendering Relational Physical ER Diagram...</div>`;
            const id = "relSvg_" + Math.floor(Math.random() * 1000000);
            const { svg } = await mermaid.render(id, RELATIONAL_ER_MERMAID_CODE);
            container.innerHTML = svg;
            relRendered = true;
            applyDiagramZoom("rel");
        }
    } catch (err) {
        console.error("Mermaid Relational ER render error:", err);
        container.innerHTML = `<div style="color:#f87171; padding:20px;">Could not render diagram: ${escapeHtml(err.message || String(err))}</div>`;
    }
}

function initEerStudio() {
    if (!eerStudioInitialized) {
        eerStudioInitialized = true;

        // View toggle buttons
        $("btnViewEer").addEventListener("click", () => {
            $("eerConceptualView").classList.remove("hidden");
            $("relationalErView").classList.add("hidden");
            $("btnViewEer").className = "button primary-button";
            $("btnViewRelationalEr").className = "button secondary-button";
            renderEerSvgDiagram();
        });

        $("btnViewRelationalEr").addEventListener("click", () => {
            $("eerConceptualView").classList.add("hidden");
            $("relationalErView").classList.remove("hidden");
            $("btnViewEer").className = "button secondary-button";
            $("btnViewRelationalEr").className = "button primary-button";
            renderRelationalErSvgDiagram();
            populateSchemaTableSelect();
        });

        // Copy Mermaid code
        $("btnCopyMermaid").addEventListener("click", () => {
            const isEer = !$("eerConceptualView").classList.contains("hidden");
            const code = isEer ? EER_MERMAID_CODE : RELATIONAL_ER_MERMAID_CODE;
            navigator.clipboard.writeText(code).then(() => {
                showToast("Mermaid diagram code copied to clipboard!");
            });
        });

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

        $("schemaTableSelect").addEventListener("change", e => {
            inspectTableSchema(e.target.value);
        });
    }

    renderEerSvgDiagram();
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