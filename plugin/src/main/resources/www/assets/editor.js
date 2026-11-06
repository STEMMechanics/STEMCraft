(() => {
    const base = location.pathname.replace(/\/$/, '');
    const q = selector => document.querySelector(selector);
    const esc = value => String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
    const apiUrl = (module, endpoint) => base + '/api/' + module + '/' + (endpoint || 'data');

    function tabsComponent(items, active) {
        return '<div class="tabs component-tabs" role="tablist">' + items.map(item =>
            '<button class="tab' + (item.id === active ? ' active' : '') + '" type="button" role="tab" aria-selected="' +
            (item.id === active ? 'true' : 'false') + '" data-editor-tab="' + esc(item.id) + '">' + esc(item.label) + '</button>').join('') + '</div>';
    }

    function bindTabs(callback) {
        q('#workspace').querySelectorAll('[data-editor-tab]').forEach(button => button.onclick = () => callback(button.dataset.editorTab));
    }

    function switchControl(label, name, checked) {
        return '<label class="switch-control"><span>' + esc(label) + '</span><input name="' + esc(name) + '" type="checkbox"' +
            (checked ? ' checked' : '') + '><span class="switch-track" aria-hidden="true"></span></label>';
    }

    function switchObjectControl(label, prop, checked) {
        return '<label class="switch-control"><span>' + esc(label) + '</span><input data-prop="' + esc(prop) + '" type="checkbox"' +
            (checked ? ' checked' : '') + '><span class="switch-track" aria-hidden="true"></span></label>';
    }

    function openEditorDialog(title, body, submitLabel, onSubmit) {
        const dialog = q('#editor-dialog');
        dialog.innerHTML = '<form id="editor-dialog-form"><div class="dialog-head"><h2 id="editor-dialog-title">' + esc(title) +
            '</h2><button class="button dialog-close" type="button" data-dialog-close aria-label="Close">×</button></div>' + body +
            '<div class="dialog-actions"><button class="button" type="button" data-dialog-close>Cancel</button><button class="button primary" type="submit">' +
            esc(submitLabel) + '</button></div></form>';
        dialog.querySelectorAll('[data-dialog-close]').forEach(button => button.onclick = () => dialog.close());
        dialog.querySelector('#editor-dialog-form').onsubmit = async event => {
            event.preventDefault();
            await onSubmit(new FormData(event.target), dialog);
        };
        dialog.showModal();
    }

    function formatValidationError(error) {
        const text = String(error || 'Validation failed.');
        const mapping = /^(.*?)\s+must be a mapping\.?$/i.exec(text);
        if (!mapping) return '<li>' + esc(text) + '</li>';
        const location = mapping[1];
        const yamlPath = /^(.*\.(?:ya?ml))\.(.+)$/i.exec(location);
        const source = yamlPath ? yamlPath[1] : location;
        const path = yamlPath ? yamlPath[2] : '';
        return '<li class="validation-error"><strong>Expected a YAML object</strong><span>' + esc(source) +
            (path ? ' → ' + esc(path) : '') + '</span><small>Use named fields under this path (for example, <code>id: value</code>), not a single value or list.</small></li>';
    }

    let modules = [];
    let activeId = null;
    let activeData = null;
    let currentDocument = null;
    let selectedItem = null;
    let selectedLoot = null;
    let selectedQuest = null;
    let selectedNpc = null;
    let questListKind = 'quests';
    let activeQuery = {};
    let configEvents = null;
    let configEventsRetry = null;
    let documentReturn = null;

    function setSidebar(open) {
        document.body.classList.toggle('sidebar-open', open);
        document.body.classList.toggle('sidebar-closed', !open);
        q('#sidebarToggle').setAttribute('aria-expanded', String(open));
        q('#sidebarToggle').setAttribute('aria-label', open ? 'Close navigation' : 'Open navigation');
        localStorage.setItem('stemcraft-editor-sidebar', open ? 'open' : 'closed');
    }

    const storedSidebar = localStorage.getItem('stemcraft-editor-sidebar');
    const defaultSidebarOpen = !window.matchMedia('(max-width: 56rem)').matches;
    setSidebar(storedSidebar ? storedSidebar === 'open' : defaultSidebarOpen);
    q('#sidebarToggle').onclick = () => setSidebar(document.body.classList.contains('sidebar-closed'));
    q('#sidebarClose').onclick = () => setSidebar(false);
    q('#sidebarBackdrop').onclick = () => setSidebar(false);

    async function request(url, options = {}) {
        const response = await fetch(url, options);
        const type = response.headers.get('content-type') || '';
        const value = type.includes('json') ? await response.json() : await response.text();
        if (!response.ok) {
            const message = typeof value === 'object' ? (value.message || value.error || JSON.stringify(value)) : value;
            throw new Error(message || ('Request failed (' + response.status + ')'));
        }
        return value;
    }

    function setStatus(message, bad = false) {
        // Operation feedback is shown in the affected panel or notice area.
    }

    function loading(message) {
        return '<div class="loading" role="status"><span class="loading-spinner" aria-hidden="true"></span><span>' + esc(message) + '</span></div>';
    }

    function notice(message, bad = false) {
        const element = q('#notice');
        element.textContent = message;
        element.className = 'notice ' + (message ? 'show ' : '') + (bad ? 'bad' : '');
    }

    function draftConflictNotice(message) {
        const element = q('#notice');
        element.className = 'notice show bad';
        element.innerHTML = '<div class="notice-content"><span>' + esc(message) + '</span>' +
            '<span class="notice-actions"><button id="draft-reload" class="button small primary" type="button">Reload</button>' +
            '<button id="draft-dismiss" class="button small" type="button">Keep draft</button></span></div>';
        q('#draft-reload').onclick = () => {
            if (!currentDocument) return;
            const documentInfo = {...currentDocument};
            notice('Reloading the latest configuration…');
            openDocument(documentInfo.module, documentInfo.pack, documentInfo.file);
        };
        q('#draft-dismiss').onclick = () => notice('');
    }

    function moduleInfo() { return modules.find(module => module.id === activeId); }

    function pageHead() { return ''; }

    function querySuffix(query) {
        const params = new URLSearchParams(Object.entries(query || {}).filter(([, value]) => value !== undefined && value !== null && value !== ''));
        const value = params.toString();
        return value ? '?' + value : '';
    }

    async function loadModuleData(id, query = {}) {
        try {
            return await request(apiUrl(id, 'view') + querySuffix(query));
        } catch (error) {
            if (error.message !== 'Declarative view not available.') throw error;
            return request(apiUrl(id, 'data') + querySuffix(query));
        }
    }

    const moduleIcons = {
        items: 'M12 3 3.5 7.5 12 12l8.5-4.5L12 3Z M3.5 7.5V16.5L12 21v-9 M20.5 7.5V16.5L12 21',
        loot: 'M6 3h12l4 6-10 12L2 9l4-6Z M2 9h20 M12 21 8 9l4-6 4 6-4 12Z',
        packs: 'M3 7.5 12 3l9 4.5-9 4.5-9-4.5Z M3 7.5V17l9 4 9-4V7.5 M12 12v9',
        quests: 'M6 4h12v16H6a3 3 0 0 1 0-6h12 M6 14h12 M9 8h6 M9 11h6',
        default: 'M4 4h6v2a2 2 0 1 0 4 0V4h6v6h-2a2 2 0 1 0 0 4h2v6h-6v-2a2 2 0 1 0-4 0v2H4v-6h2a2 2 0 1 0 0-4H4V4Z'
    };

    function moduleIcon(module) {
        return moduleIcons[module.id] || moduleIcons.default;
    }

    function iconSvg(path, className = '') {
        return '<svg class="ui-icon ' + esc(className) + '" viewBox="0 0 24 24" focusable="false" aria-hidden="true"><path d="' + esc(path) + '"></path></svg>';
    }

    function renderNav() {
        q('#moduleNav').innerHTML = modules.map(module =>
            '<button class="' + (module.id === activeId ? 'active' : '') + '" data-module="' + esc(module.id) +
            '" type="button"><span class="module-icon" aria-hidden="true">' + iconSvg(moduleIcon(module), 'module-icon-svg') +
            '</span><span>' + esc(module.title) + '</span></button>').join('');
        q('#moduleNav').querySelectorAll('[data-module]').forEach(button => {
            button.onclick = () => { setSidebar(false); openModule(button.dataset.module); };
        });
    }

    async function openModule(id, query = {}) {
        activeId = id;
        activeQuery = {...query};
        currentDocument = null;
        selectedItem = null;
        selectedLoot = null;
        selectedQuest = null;
        selectedNpc = null;
        questListKind = 'quests';
        renderNav();
        const info = moduleInfo();
        q('#pageTitle').textContent = info?.title || 'STEMCraft Editor';
        q('#workspace').innerHTML = loading('Opening editor…');
        notice('');
        try {
            activeData = await loadModuleData(id, activeQuery);
            renderModule();
            setStatus('Ready');
        } catch (error) {
            q('#workspace').innerHTML = '<div class="empty">' + esc(error.message) + '</div>';
            setStatus('Could not open', true);
        }
    }

    function renderModule() {
        if (!activeData) return;
        if (Array.isArray(activeData.components)) return renderDeclarativeView(activeData);
        if (activeData.kind === 'components') return renderComponents();
        if (activeData.kind === 'quests') return renderQuests(activeData);
        if (activeData.kind === 'items') return renderItems(activeData);
        if (activeData.kind === 'loot') return renderLoot(activeData);
        q('#workspace').innerHTML = '<div class="empty">This editor is not available yet.</div>';
    }

    function subviewHead(title, backLabel = 'Back') {
        return '<div class="subview-head"><button class="button subview-back" type="button" data-subview-back>← ' +
            esc(backLabel) + '</button><h2>' + esc(title) + '</h2></div>';
    }

    let viewActionSequence = 0;
    const viewActions = new Map();

    function viewActionButton(action, extraClass = '') {
        if (!action) return '';
        const key = 'view-action-' + (++viewActionSequence);
        viewActions.set(key, action);
        const variant = ['primary', 'good', 'warn', 'danger'].includes(action.variant) ? ' ' + action.variant : '';
        return '<button class="button small' + variant + (extraClass ? ' ' + extraClass : '') + '" type="button" data-view-action="' +
            esc(key) + '">' + esc(action.label) + '</button>';
    }

    function viewBadges(badges) {
        return (Array.isArray(badges) ? badges : []).map(badge =>
            '<span class="pill ' + (['good', 'bad', 'warn'].includes(badge.tone) ? esc(badge.tone) : '') + '">' +
            esc(badge.text) + '</span>').join('');
    }

    function viewField(field) {
        const type = field.type || 'text';
        let control;
        if (type === 'select') {
            control = '<select name="' + esc(field.name) + '">' + (Array.isArray(field.options) ? field.options : []).map(option =>
                '<option value="' + esc(option.value) + '">' + esc(option.label) + '</option>').join('') + '</select>';
        } else if (type === 'suggestion') {
            const listId = 'view-suggestions-' + esc(field.name);
            control = '<input name="' + esc(field.name) + '" list="' + listId + '" placeholder="' + esc(field.placeholder || '') + '"' +
                (field.required ? ' required' : '') + (field.pattern ? ' pattern="' + esc(field.pattern) + '"' : '') + '>' +
                '<datalist id="' + listId + '">' + (Array.isArray(field.options) ? field.options : []).map(option =>
                    '<option value="' + esc(option.value) + '">' + esc(option.label) + '</option>').join('') + '</datalist>';
        } else if (type === 'textarea') {
            control = '<textarea name="' + esc(field.name) + '" placeholder="' + esc(field.placeholder || '') + '"' +
                (field.required ? ' required' : '') + '>' + esc(field.value || '') + '</textarea>';
        } else {
            control = '<input name="' + esc(field.name) + '" type="' + esc(type) + '" value="' + esc(field.value || '') +
                '" placeholder="' + esc(field.placeholder || '') + '"' + (field.required ? ' required' : '') +
                (field.pattern ? ' pattern="' + esc(field.pattern) + '"' : '') + '>';
        }
        return '<label>' + esc(field.label) + control + '</label>';
    }

    function openDeclarativeDialog(action) {
        const dialog = action.dialog;
        const fields = (dialog?.fields || []).map(viewField).join('');
        openEditorDialog(dialog.title, '<div class="field-grid">' + fields + '</div>', dialog.submitLabel, async (form, element) => {
            try {
                const payload = {...(action.payload || {})};
                (dialog.fields || []).forEach(field => payload[field.name] = form.get(field.name) ?? '');
                const result = await request(apiUrl(activeId, action.endpoint), {
                    method: action.method || 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(payload)
                });
                element.close();
                notice(result.message || 'Saved.');
                await openModule(activeId, activeQuery);
            } catch (error) { notice(error.message, true); setStatus('Needs attention', true); }
        });
    }

    async function dispatchViewAction(action) {
        if (!action) return;
        if (action.type === 'navigate') {
            await openModule(activeId, action.query || {});
            return;
        }
        if (action.type === 'dialog') {
            openDeclarativeDialog(action);
            return;
        }
        if (action.type !== 'request') return;
        try {
            setStatus('Working…');
            const result = await request(apiUrl(activeId, action.endpoint), {
                method: action.method || 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(action.payload || {})
            });
            notice(result.message || 'Done.');
            await openModule(activeId, activeQuery);
        } catch (error) { notice(error.message, true); setStatus('Needs attention', true); }
    }

    function renderDeclarativeComponent(component) {
        const type = component.type;
        const properties = component.properties || {};
        if (type === 'toolbar') {
            return '<div class="section-heading editor-section-heading"><div><h2>' + esc(activeData.title || '') + '</h2>' +
                (activeData.description ? '<p class="tiny">' + esc(activeData.description) + '</p>' : '') + '</div><div class="editor-header-actions">' +
                (properties.actions || []).map(viewActionButton).join('') + (properties.meta ? '<span class="tiny">' + esc(properties.meta) + '</span>' : '') + '</div></div>';
        }
        if (type === 'cards') {
            const items = Array.isArray(properties.items) ? properties.items : [];
            if (!items.length) return '<div class="empty">' + esc(properties.empty || 'Nothing found.') + '</div>';
            return '<div class="pack-grid">' + items.map(item => {
                const actionKey = item.action ? 'view-item-' + (++viewActionSequence) : '';
                if (item.action) viewActions.set(actionKey, item.action);
                return '<article class="card pack-card"' + (item.action ? ' tabindex="0" role="button" data-view-action="' + esc(actionKey) + '"' : '') + '>' +
                    '<div class="pack-card-icon" aria-hidden="true">' + iconSvg(moduleIcons[activeId] || moduleIcons.default) + '</div>' +
                    '<div class="pack-card-main"><div class="pack-card-title"><h3>' + esc(item.title) + '</h3></div>' +
                    (item.description ? '<p class="pack-card-description">' + esc(item.description) + '</p>' : '') +
                    '<div class="pack-card-meta">' + viewBadges(item.badges) + '</div></div></article>';
            }).join('') + '</div>';
        }
        if (type === 'subview') {
            return '<div class="subview-head"><button class="button subview-back" type="button" data-view-action="' +
                esc(registerViewAction(properties.back)) + '">← Back</button><h2>' + esc(properties.title || '') + '</h2></div>';
        }
        if (type === 'card') {
            return '<section class="card"><div class="document-head"><div><h3>' + esc(properties.title || '') + '</h3>' +
                (properties.description ? '<p>' + esc(properties.description) + '</p>' : '') + '</div>' + viewBadges(properties.badges) + '</div>' +
                (Array.isArray(properties.children) ? properties.children.map(renderDeclarativeComponent).join('') : '') +
                (Array.isArray(properties.actions) ? '<div class="card-actions">' + properties.actions.map(viewActionButton).join('') + '</div>' : '') + '</section>';
        }
        if (type === 'validation') {
            const errors = Array.isArray(properties.errors) ? properties.errors : [];
            return errors.length ? '<div><h3>Validation issues</h3><ul class="errors pack-error-list">' + errors.map(formatValidationError).join('') + '</ul></div>' :
                '<p class="muted">' + esc(properties.empty || 'No validation issues found.') + '</p>';
        }
        if (type === 'meta') {
            const dependencies = Array.isArray(properties.dependencies) && properties.dependencies.length ?
                '<div><dt>Dependencies</dt><dd>' + esc(properties.dependencies.join(', ')) + '</dd></div>' : '';
            const resource = properties.resourcePackRebuildRequired ? '<div><dt>Resource pack</dt><dd>Rebuild required after changes.</dd></div>' : '';
            return dependencies || resource ? '<dl class="pack-detail-meta">' + dependencies + resource + '</dl>' : '';
        }
        if (type === 'document-picker') {
            const selectId = 'view-document-picker-' + (++viewActionSequence);
            const options = Array.isArray(properties.options) ? properties.options : [];
            return '<div class="card-actions"><select id="' + selectId + '"><option value="">Choose a file</option>' +
                options.map(option => '<option value="' + esc(option.value) + '">' + esc(option.label) + '</option>').join('') +
                '</select><button class="button small primary" type="button" data-view-document="' + esc(selectId) + '" data-view-document-module="' +
                esc(properties.module || activeId) + '" data-view-document-pack="' + esc(properties.pack || '') + '">Open file</button></div>';
        }
        if (type === 'empty') return '<div class="empty">' + esc(properties.text || 'Nothing found.') + '</div>';
        return '';
    }

    function registerViewAction(action) {
        const key = 'view-action-' + (++viewActionSequence);
        viewActions.set(key, action);
        return key;
    }

    function openDeclarativeDocument(module, pack, file) {
        if (!file) return notice('Choose a file first.');
        q('#workspace').innerHTML = subviewHead([pack, file].filter(Boolean).join(' / ')) + '<section class="card"><div id="document-panel"></div></section>';
        q('[data-subview-back]').onclick = () => openModule(activeId, activeQuery);
        openDocument(module, pack, file);
    }

    function bindDeclarativeActions() {
        q('#workspace').querySelectorAll('[data-view-action]').forEach(element => {
            const action = viewActions.get(element.dataset.viewAction);
            element.onclick = event => { event.stopPropagation(); dispatchViewAction(action); };
            element.onkeydown = event => {
                if ((event.key === 'Enter' || event.key === ' ') && element.getAttribute('role') === 'button') {
                    event.preventDefault(); element.click();
                }
            };
        });
        q('#workspace').querySelectorAll('[data-view-document]').forEach(button => button.onclick = () => {
            const select = q('#' + button.dataset.viewDocument);
            openDeclarativeDocument(button.dataset.viewDocumentModule, button.dataset.viewDocumentPack, select?.value || '');
        });
    }

    function renderDeclarativeView(view) {
        viewActionSequence = 0;
        viewActions.clear();
        q('#workspace').innerHTML = '<div class="stack">' + (view.components || []).map(renderDeclarativeComponent).join('') + '</div>';
        bindDeclarativeActions();
    }

    function renderComponents() {
        q('#workspace').innerHTML = pageHead('Components') +
            '<div class="component-showcase">' +
            '<section class="card component-demo"><h3>Actions and status</h3><div class="demo-row"><span class="component-label">Buttons</span>' +
            '<button class="button primary" type="button">Primary action</button><button class="button" type="button">Secondary action</button>' +
            '<button class="button small" type="button">Small action</button><button class="button good" type="button">Enabled</button>' +
            '<button class="button warn" type="button">Warning</button><button class="button danger" type="button">Delete</button><button id="component-dialog-demo" class="button small" type="button">Dialog</button></div>' +
            '<div class="demo-row"><span class="component-label">Status</span><span class="pill good">Saved</span><span class="pill warn">Draft</span><span class="pill bad">Needs attention</span><span class="status">Supporting text</span></div></section>' +
            '<section class="card component-demo"><h3>Tabs</h3>' + tabsComponent([{id: 'overview', label: 'Overview'}, {id: 'settings', label: 'Settings'}, {id: 'history', label: 'History'}], 'overview') +
            '<span class="tiny">Use tabs for sibling views within a module.</span></section>' +
            '<section class="card component-demo"><h3>Forms</h3><div class="field-grid"><label>Text field<input value="Example value"></label>' +
            '<label>Select<select><option>First option</option><option>Second option</option></select></label>' +
            '<label>Suggestions<input list="component-suggestions" placeholder="Choose or type…"><datalist id="component-suggestions"><option value="First suggestion"><option value="Second suggestion"></datalist></label>' +
            '<label class="wide">Long text<textarea class="small-area">A shared editor field with the site theme.</textarea></label>' +
            switchControl('Toggle switch', 'component-toggle', true) + '<label class="wide">Range slider<input type="range" min="0" max="100" value="65"><span class="tiny">65%</span></label></div></section>' +
            '<section class="card component-demo"><h3>Lists and tables</h3><div class="grid"><div><span class="component-label">List</span><div class="list">' +
            '<button class="list-button active" type="button"><strong>Selected item</strong><small>items/example</small></button>' +
            '<button class="list-button" type="button"><strong>Another item</strong><small>items/another</small></button></div></div>' +
            '<div class="table-wrap"><span class="component-label">Table</span><table class="data-table"><thead><tr><th>Name</th><th>State</th><th>Action</th></tr></thead><tbody>' +
            '<tr><td>Example row</td><td><span class="pill good">Ready</span></td><td><button class="button small" type="button">Open</button></td></tr>' +
            '<tr><td>Another row</td><td><span class="pill">Draft</span></td><td><button class="button small" type="button">Open</button></td></tr></tbody></table></div></div></section>' +
            '<section class="card component-demo"><h3>Feedback and states</h3><div class="notice show">A non-blocking notice with an optional action.</div>' +
            '<div class="loading"><span class="loading-spinner" aria-hidden="true"></span><span>Loading state</span></div>' +
            '<div class="empty">Empty state content appears here when there is nothing to show.</div></section></div>';
        q('#component-dialog-demo').onclick = () => openEditorDialog('Dialog component', '<p class="muted">Dialogs keep short create and edit flows in context without adding another page panel.</p>', 'Done', async (form, dialog) => dialog.close());
    }

    function questOptions(values, current, blank = true) {
        return (blank ? '<option value="">None</option>' : '') + values.map(value =>
            '<option value="' + esc(value) + '"' + (value === current ? ' selected' : '') + '>' + esc(value) + '</option>').join('');
    }

    function questField(label, name, value, type = 'text', wide = false) {
        return '<label' + (wide ? ' class="wide"' : '') + '>' + esc(label) + '<input name="' + esc(name) + '" type="' +
            esc(type) + '" value="' + esc(value) + '"></label>';
    }

    function questArea(label, name, value, wide = true) {
        return '<label' + (wide ? ' class="wide"' : '') + '>' + esc(label) + '<textarea name="' + esc(name) + '">' +
            esc(value) + '</textarea></label>';
    }

    function questObjectField(label, prop, value, type = 'text') {
        return '<label>' + esc(label) + '<input data-prop="' + esc(prop) + '" type="' + esc(type) + '" value="' + esc(value) + '"></label>';
    }

    function questObjectArea(label, prop, value) {
        return '<label class="wide">' + esc(label) + '<textarea data-prop="' + esc(prop) + '">' + esc(value) + '</textarea></label>';
    }

    function questDepth(data, quest, seen = new Set()) {
        if (!quest || seen.has(quest.id)) return 0;
        seen.add(quest.id);
        const parents = (quest.requirements || []).map(id => data.quests.find(value => value.id === id)).filter(Boolean);
        return parents.length ? 1 + Math.max(...parents.map(parent => questDepth(data, parent, new Set(seen)))) : 0;
    }

    function questLevel(data, quest) {
        return data.npcs.find(npc => npc.id === quest.startNpcProfile)?.minimumLevel ?? 0;
    }

    function questSort(data, first, second) {
        return questLevel(data, first) - questLevel(data, second) || String(first.title).localeCompare(String(second.title));
    }

    function orderedQuestValues(data) {
        const visited = new Set();
        const result = [];
        const primaryParent = quest => data.quests.find(value => value.id === (quest.requirements || [])[0]);
        const visit = quest => {
            if (!quest || visited.has(quest.id)) return;
            visited.add(quest.id);
            result.push(quest);
            data.quests.filter(child => primaryParent(child)?.id === quest.id).sort((a, b) => questSort(data, a, b)).forEach(visit);
        };
        data.quests.filter(quest => !primaryParent(quest)).sort((a, b) => questSort(data, a, b)).forEach(visit);
        data.quests.filter(quest => !visited.has(quest.id)).sort((a, b) => questSort(data, a, b)).forEach(visit);
        return result;
    }

    function questObjectiveTarget(data, objective) {
        if (objective.type === 'COLLECT') return '<label>Material<select data-prop="target">' +
            questOptions(data.materials || [], objective.target, false) + '</select></label>';
        if (['KILL', 'INTERACT'].includes(objective.type)) return '<label>Entity type<select data-prop="target">' +
            questOptions(data.entityTypes || [], objective.target, false) + '</select></label>';
        if (objective.type === 'NPC') return '<label>NPC profile<select data-prop="target">' +
            questOptions((data.npcs || []).map(npc => 'profile:' + npc.id), objective.target, false) + '</select></label>';
        if (objective.type === 'STRUCTURE') return '<label>Structure<select data-prop="target">' +
            questOptions(['SHIPWRECK', 'SHIPWRECK_BEACHED', 'PILLAGER_OUTPOST', 'OCEAN_RUIN_COLD', 'OCEAN_RUIN_WARM', 'RUINED_PORTAL', 'MINESHAFT', 'BURIED_TREASURE'], objective.target, false) + '</select></label>';
        if (['ALTITUDE_ABOVE', 'ALTITUDE_BELOW', 'UNDERWATER'].includes(objective.type)) return questObjectField('Y level', 'target', objective.target, 'number');
        if (objective.type === 'BIOME') return questObjectField('Biome key', 'target', objective.target);
        return '<label>Target<input value="Automatic" disabled></label>';
    }

    function questObjectiveRow(data, objective, index) {
        const types = ['COLLECT', 'KILL', 'LOCATION', 'NPC', 'BIOME', 'ALTITUDE_ABOVE', 'ALTITUDE_BELOW', 'STRUCTURE', 'SLEEP', 'INTERACT', 'UNDERWATER', 'NIGHT'];
        return '<div class="card" data-objective="' + index + '"><div class="document-head"><strong>Objective ' + (index + 1) + '</strong>' +
            '<div class="card-actions"><button data-up="objective" class="button small" type="button">↑</button><button data-down="objective" class="button small" type="button">↓</button>' +
            '<button data-remove="objective" class="button danger small" type="button">Remove</button></div></div><div class="field-grid">' +
            '<label>Type<select data-prop="type">' + questOptions(types, objective.type, false) + '</select></label>' + questObjectiveTarget(data, objective) +
            questObjectField('Amount', 'amount', objective.amount, 'number') + questObjectField('Player-facing objective', 'label', objective.label) +
            switchObjectControl('Consume collected items', 'consume', objective.consume) +
            questObjectField('World', 'world', objective.world || 'survival') + questObjectField('X', 'x', objective.x, 'number') + questObjectField('Y', 'y', objective.y, 'number') +
            questObjectField('Z', 'z', objective.z, 'number') + questObjectField('Radius', 'radius', objective.radius, 'number') + '</div></div>';
    }

    function questRewardRow(reward, index) {
        return '<div class="card" data-reward="' + index + '"><div class="document-head"><strong>Item ' + (index + 1) + '</strong>' +
            '<button data-remove="reward" class="button danger small" type="button">Remove</button></div><div class="field-grid">' +
            questObjectField('Material', 'material', reward.material) + questObjectField('Amount', 'amount', reward.amount, 'number') +
            questObjectField('Name', 'name', reward.name || '') + questObjectArea('Lore — one line per entry', 'lore', (reward.lore || []).join('\n')) +
            switchObjectControl('Unbreakable', 'unbreakable', reward.unbreakable) + '</div></div>';
    }

    function defaultQuestTarget(data, type) {
        return {COLLECT: 'OAK_LOG', KILL: 'ZOMBIE', NPC: data.npcs[0] ? 'profile:' + data.npcs[0].id : '', BIOME: 'plains',
            ALTITUDE_ABOVE: '120', ALTITUDE_BELOW: '0', UNDERWATER: '50', STRUCTURE: 'SHIPWRECK', INTERACT: 'IRON_GOLEM'}[type] || '';
    }

    function renderQuests(data) {
        if (selectedQuest) {
            const quest = (data.quests || []).find(value => value.id === selectedQuest);
            if (quest) return renderQuestSubview(data, quest);
            selectedQuest = null;
        }
        if (selectedNpc) {
            const npc = (data.npcs || []).find(value => value.id === selectedNpc);
            if (npc) return renderNpcSubview(data, npc);
            selectedNpc = null;
        }
        const list = questListKind === 'npcs' ? (data.npcs || []) : orderedQuestValues(data);
        const search = q('#quest-search-value')?.value?.toLowerCase() || '';
        const filtered = list.filter(value => String(value.title || value.name || '').toLowerCase().includes(search) || value.id.toLowerCase().includes(search));
        const entries = filtered.map(value => {
            const isQuest = questListKind === 'quests';
            const label = isQuest ? value.title : value.name;
            const detail = isQuest ? value.id + ' · level ' + questLevel(data, value) : value.id + ' · ' + value.entityType;
            return '<button class="list-button" type="button" data-quest-select="' + esc(value.id) + '"><strong>' + esc(label) +
                '</strong><small>' + esc(detail) + '</small></button>';
        }).join('');
        const writable = Boolean(moduleInfo()?.writable);
        q('#workspace').innerHTML = pageHead('Quests') +
            '<div class="section-heading editor-section-heading"><div><h2>Quest definitions</h2><p class="tiny">Open a quest or NPC in its own editor view.</p></div>' +
            '<div class="card-actions"><button id="quest-yaml" class="button small" type="button">Advanced YAML</button><span class="tiny">' + filtered.length + ' shown</span></div></div>' +
            tabsComponent([{id: 'quests', label: 'Quests'}, {id: 'npcs', label: 'NPCs'}], questListKind) +
            '<section class="card"><div class="field-grid"><label class="wide">Search<input id="quest-search-value" value="' + esc(search) + '" placeholder="Search quests and NPCs"></label></div>' +
            '<div class="list">' + (entries || '<div class="empty">No quest definitions found.</div>') + '</div>' +
            (writable ? '<div class="card-actions"><button id="quest-new" class="button primary" type="button">+ New ' + (questListKind === 'quests' ? 'quest' : 'NPC') + '</button></div>' : '') +
            '</section>';
        q('#quest-search-value').oninput = event => {
            const value = event.target.value;
            renderQuests({...data, _search: value});
            const searchInput = q('#quest-search-value');
            if (searchInput) { searchInput.focus(); searchInput.setSelectionRange(value.length, value.length); }
        };
        bindTabs(tab => {
            questListKind = tab;
            selectedQuest = null;
            selectedNpc = null;
            renderQuests(activeData);
        });
        q('#workspace').querySelectorAll('[data-quest-select]').forEach(button => button.onclick = () => {
            selectedQuest = questListKind === 'quests' ? button.dataset.questSelect : null;
            selectedNpc = questListKind === 'npcs' ? button.dataset.questSelect : null;
            renderQuests(activeData);
        });
        q('#quest-yaml').onclick = () => openQuestDocument('quests.yml');
        const create = q('#quest-new');
        if (create) create.onclick = () => createQuestEditorEntry();
    }

    function createQuestEditorEntry() {
        const id = prompt('New ' + (questListKind === 'quests' ? 'quest' : 'NPC') + ' ID (lowercase):');
        if (!id) return;
        const values = questListKind === 'quests' ? activeData.quests : activeData.npcs;
        if (values.some(value => value.id === id)) return notice('That ID already exists.', true);
        if (questListKind === 'quests') {
            activeData.quests.push({id, title: 'New Quest', author: 'Quest Giver', description: 'Tell this quest as a story.', shortDescription: '',
                rewardText: 'A reward awaits.', enabled: false, repeatable: false, timeLimitSeconds: 0, restartCooldownSeconds: 0, globalMaxCompletions: 0,
                startNpcProfile: null, endNpcProfile: null, startNpcName: 'Quest Giver', endNpcName: 'Quest Contact',
                dialogue: {offer: [], idle: [], incomplete: [], objective: [], complete: []}, requirements: [], objectives: [], rewardItems: [], rewardCommands: []});
            selectedQuest = id;
            selectedNpc = null;
        } else {
            activeData.npcs.push({id, name: 'New NPC', entityType: 'VILLAGER', ai: true, world: 'survival', minDistance: 30, maxDistance: 100,
                uniquenessRadius: 1200, despawnRadius: 150, minimumLevel: 0, timeFrom: 0, timeUntil: 24000, dailyChance: .25, lifetimeSeconds: 0, biomes: [], idleDialogue: []});
            selectedNpc = id;
            selectedQuest = null;
        }
        notice('New definition added. Save it from the editor view.');
        renderQuests(activeData);
    }

    async function saveQuestData() {
        try {
            setStatus('Saving quest data…');
            const result = await request(apiUrl('quests', 'data'), {method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(activeData)});
            notice(result.message || 'Quest data saved.');
            activeData = await request(apiUrl('quests', 'data'));
            selectedQuest = null;
            selectedNpc = null;
            renderQuests(activeData);
            setStatus('Ready');
        } catch (error) {
            notice(error.message, true);
            setStatus('Needs attention', true);
        }
    }

    function renderQuestSubview(data, quest) {
        const profiles = (data.npcs || []).map(npc => npc.id);
        const otherQuests = (data.quests || []).filter(value => value.id !== quest.id).map(value => value.id);
        const dialogue = quest.dialogue || {};
        q('#workspace').innerHTML = subviewHead(quest.title || quest.id) +
            '<form id="quest-editor-form" class="stack"><section class="card"><h3>Story</h3><div class="field-grid">' +
            questField('Quest ID', 'id', quest.id) + questField('Title', 'title', quest.title) + questField('Author / giver', 'author', quest.author) +
            switchControl('Enabled', 'enabled', quest.enabled) + switchControl('Repeatable', 'repeatable', quest.repeatable) +
            questArea('Quest-giver story', 'description', quest.description) +
            questArea('Short description', 'shortDescription', quest.shortDescription || '') + '</div></section>' +
            '<section class="card"><h3>Characters</h3><div class="field-grid"><label>Starting NPC<select name="startNpcProfile">' + questOptions(profiles, quest.startNpcProfile) +
            '</select></label><label>Turn-in NPC<select name="endNpcProfile">' + questOptions(profiles, quest.endNpcProfile) + '</select></label>' +
            questField('Starting NPC display name', 'startNpcName', quest.startNpcName) + questField('Turn-in NPC display name', 'endNpcName', quest.endNpcName) + '</div></section>' +
            '<section class="card"><h3>Availability and failure</h3><div class="field-grid">' + questField('Time limit in seconds (0 = none)', 'timeLimitSeconds', quest.timeLimitSeconds || 0, 'number') +
            questField('Restart cooldown in seconds', 'restartCooldownSeconds', quest.restartCooldownSeconds || 0, 'number') + questField('Global completion limit (0 = unlimited)', 'globalMaxCompletions', quest.globalMaxCompletions || 0, 'number') + '</div></section>' +
            '<section class="card"><h3>Requirements</h3><label>Required quests<select name="requirements" multiple size="' + Math.min(8, Math.max(3, otherQuests.length)) + '">' +
            otherQuests.map(id => '<option value="' + esc(id) + '"' + ((quest.requirements || []).includes(id) ? ' selected' : '') + '>' + esc(id) + '</option>').join('') + '</select></label></section>' +
            '<section class="card"><div class="document-head"><h3>Objectives</h3><button data-add="objective" class="button small" type="button">+ Objective</button></div><div class="stack">' +
            (quest.objectives || []).map((objective, index) => questObjectiveRow(data, objective, index)).join('') + '</div></section>' +
            '<section class="card"><h3>Rewards</h3>' + questArea('Reward description', 'rewardText', quest.rewardText, false) +
            '<div id="quest-rewards" class="stack">' + (quest.rewardItems || []).map((reward, index) => questRewardRow(reward, index)).join('') + '</div>' +
            '<div class="card-actions"><button data-add="reward" class="button small" type="button">+ Item reward</button></div>' +
            questArea('Console commands — one per line', 'rewardCommands', (quest.rewardCommands || []).join('\n')) + '</section>' +
            '<section class="card"><h3>Dialogue</h3><div class="field-grid">' +
            ['offer', 'idle', 'incomplete', 'objective', 'complete'].map(state => questArea(state[0].toUpperCase() + state.slice(1) + ' — one saying per line', 'dialogue.' + state, (dialogue[state] || []).join('\n'), state === 'complete')).join('') +
            '</div></section><div class="card-actions"><button class="button primary" type="submit">Save quest</button><button id="quest-duplicate" class="button" type="button">Duplicate</button>' +
            '<button id="quest-delete" class="button danger" type="button">Delete quest</button></div></form>';
        bindQuestSubview(data, quest, true);
    }

    function renderNpcSubview(data, npc) {
        const starts = (data.quests || []).filter(quest => quest.startNpcProfile === npc.id);
        const ends = (data.quests || []).filter(quest => quest.endNpcProfile === npc.id);
        const linked = values => values.length ? values.map(quest => '<button class="button small" data-open-quest="' + esc(quest.id) + '" type="button">' +
            esc(quest.title) + '</button>').join(' ') : '<span class="muted">None</span>';
        q('#workspace').innerHTML = subviewHead(npc.name || npc.id) +
            '<form id="npc-editor-form" class="stack"><section class="card"><h3>Identity</h3><div class="field-grid">' + questField('NPC ID', 'id', npc.id) +
            questField('Visible name', 'name', npc.name) + '<label>Entity type<select name="entityType">' + questOptions(data.entityTypes || [], npc.entityType, false) +
            '</select></label>' + switchControl('Can walk around', 'ai', npc.ai) + '</div></section>' +
            '<section class="card"><h3>Linked quests</h3><div class="field-grid"><div><span class="component-label">Starts here</span><div class="card-actions">' + linked(starts) +
            '</div></div><div><span class="component-label">Handed in here</span><div class="card-actions">' + linked(ends) + '</div></div></div></section>' +
            '<section class="card"><h3>Spawn conditions</h3><div class="field-grid">' + questField('World', 'world', npc.world) + questField('Minimum player level', 'minimumLevel', npc.minimumLevel, 'number') +
            questField('Daily chance (0–1)', 'dailyChance', npc.dailyChance, 'number') + questField('Minimum distance', 'minDistance', npc.minDistance, 'number') +
            questField('Maximum distance', 'maxDistance', npc.maxDistance, 'number') + questField('Uniqueness radius', 'uniquenessRadius', npc.uniquenessRadius, 'number') +
            questField('Despawn radius', 'despawnRadius', npc.despawnRadius, 'number') + questField('Time from (ticks)', 'timeFrom', npc.timeFrom, 'number') +
            questField('Time until (ticks)', 'timeUntil', npc.timeUntil, 'number') + questField('Maximum appearance in seconds (0 = unlimited)', 'lifetimeSeconds', npc.lifetimeSeconds || 0, 'number') +
            questArea('Biomes — one per line', 'biomes', (npc.biomes || []).join('\n')) + '</div></section>' +
            '<section class="card"><h3>Dialogue</h3>' + questArea('Idle sayings — one per line', 'idleDialogue', (npc.idleDialogue || []).join('\n'), false) +
            '</section><div class="card-actions"><button class="button primary" type="submit">Save NPC</button><button id="npc-delete" class="button danger" type="button">Delete NPC</button></div></form>';
        bindQuestSubview(data, npc, false);
        q('#workspace').querySelectorAll('[data-open-quest]').forEach(button => button.onclick = () => {
            selectedNpc = null;
            selectedQuest = button.dataset.openQuest;
            renderQuests(activeData);
        });
    }

    function bindQuestSubview(data, value, isQuest) {
        q('[data-subview-back]').onclick = () => {
            selectedQuest = null;
            selectedNpc = null;
            renderQuests(activeData);
        };
        const form = q(isQuest ? '#quest-editor-form' : '#npc-editor-form');
        form.oninput = event => {
            const element = event.target;
            if (element.name) {
                if (element.name === 'requirements') value.requirements = [...element.selectedOptions].map(option => option.value);
                else if (element.name.startsWith('dialogue.')) {
                    value.dialogue ??= {};
                    value.dialogue[element.name.slice(9)] = String(element.value).split('\n').map(line => line.trim()).filter(Boolean);
                } else if (['rewardCommands', 'biomes', 'idleDialogue'].includes(element.name)) {
                    value[element.name] = String(element.value).split('\n').map(line => line.trim()).filter(Boolean);
                } else value[element.name] = element.type === 'checkbox' ? element.checked : element.type === 'number' ? Number(element.value) : element.value;
            } else if (element.dataset.prop) {
                const row = element.closest('[data-objective], [data-reward]');
                const values = row.hasAttribute('data-objective') ? value.objectives : value.rewardItems;
                const index = Number(row.dataset.objective ?? row.dataset.reward);
                values[index][element.dataset.prop] = element.dataset.prop === 'lore'
                    ? String(element.value).split('\n').map(line => line.trim()).filter(Boolean)
                    : element.type === 'checkbox' ? element.checked : element.type === 'number' ? Number(element.value) : element.value;
            }
        };
        form.onchange = event => {
            const element = event.target;
            if (element.dataset.prop !== 'type') return;
            const row = element.closest('[data-objective]');
            const index = Number(row.dataset.objective);
            value.objectives[index].target = defaultQuestTarget(data, element.value);
            renderQuests(activeData);
        };
        form.onclick = event => {
            const button = event.target.closest('button');
            if (!button) return;
            if (button.dataset.add === 'objective') value.objectives.push({type: 'COLLECT', target: 'OAK_LOG', amount: 1, consume: false, label: 'Collect an item', world: 'survival', x: 0, y: 64, z: 0, radius: 2});
            if (button.dataset.add === 'reward') value.rewardItems.push({material: 'EMERALD', amount: 1, name: '', lore: [], unbreakable: false});
            if (button.dataset.remove) {
                const row = button.closest('[data-objective], [data-reward]');
                const values = button.dataset.remove === 'objective' ? value.objectives : value.rewardItems;
                values.splice(Number(row.dataset.objective ?? row.dataset.reward), 1);
            }
            if (button.dataset.up || button.dataset.down) {
                const row = button.closest('[data-objective]');
                const index = Number(row.dataset.objective);
                const target = index + (button.dataset.up ? -1 : 1);
                if (target >= 0 && target < value.objectives.length) [value.objectives[index], value.objectives[target]] = [value.objectives[target], value.objectives[index]];
            }
            if (button.id === 'quest-duplicate') {
                const id = prompt('ID for duplicate:', value.id + '-copy');
                if (!id || activeData.quests.some(quest => quest.id === id)) return;
                const copy = structuredClone(value);
                copy.id = id;
                copy.title += ' Copy';
                activeData.quests.push(copy);
                selectedQuest = id;
            }
            if (button.id === 'quest-delete' || button.id === 'npc-delete') {
                if (!confirm('Delete ' + value.id + '?')) return;
                const values = isQuest ? activeData.quests : activeData.npcs;
                values.splice(values.indexOf(value), 1);
                selectedQuest = null;
                selectedNpc = null;
                saveQuestData();
                return;
            }
            if (button.dataset.add || button.dataset.remove || button.dataset.up || button.dataset.down || button.id === 'quest-duplicate') {
                event.preventDefault();
                renderQuests(activeData);
            }
        };
        form.onsubmit = event => {
            event.preventDefault();
            saveQuestData();
        };
    }

    function openQuestDocument(file) {
        selectedQuest = null;
        selectedNpc = null;
        q('#workspace').innerHTML = subviewHead(file) + '<section class="card"><div id="document-panel"></div></section>';
        q('[data-subview-back]').onclick = () => renderQuests(activeData);
        openDocument('quests', '', file);
    }

    function renderItems(data) {
        const items = data.items || [];
        const writable = Boolean(moduleInfo()?.writable);
        if (selectedItem) {
            const item = items.find(value => value.id === selectedItem);
            if (!item) { selectedItem = null; return renderItems(data); }
            q('#workspace').innerHTML = subviewHead(item.displayName || item.id) + '<section id="item-form" class="card"></section>';
            q('[data-subview-back]').onclick = () => { selectedItem = null; renderItems(activeData); };
            renderItemForm(item);
            return;
        }
        const defaultPack = (data.packs || []).find(pack => pack.enabled)?.id || data.packs?.[0]?.id || '';
        const packOptions = (data.packs || []).map(pack => '<option value="' + esc(pack.id) + '"' +
            (pack.id === defaultPack ? ' selected' : '') + '>' + esc(pack.id) + (pack.enabled ? '' : ' (disabled)') + '</option>').join('');
        const materialOptions = (data.materials || []).map(material => '<option value="' + esc(material) + '"></option>').join('');
        const list = items.map(item => '<button class="list-button" data-item="' + esc(item.id) + '" type="button"><strong>' +
            esc(item.displayName) + '</strong><small>' + esc(item.id) + ' · ' + esc(item.material) + '</small></button>').join('');
        const create = writable ? '<form id="item-create"><div class="field-grid"><label>Pack<select name="pack">' + packOptions +
            '</select></label><label>File<input name="file" value="configs/items.yml"></label><label>Item ID<input name="id" pattern="[a-z0-9][a-z0-9_-]*" placeholder="forest-shard" required></label>' +
            '<label>Material<input name="material" list="item-materials" placeholder="AMETHYST_SHARD" required></label><label class="wide">Name (optional)<input name="name" placeholder="Forest Shard"></label></div>' +
            '<div class="card-actions"><button class="button primary" type="submit">Create item</button></div></form>' : '';
        const createPanel = writable ? '<section class="card"><div class="section-heading"><h3>' + iconSvg(moduleIcons.items, 'section-icon') +
            'New item</h3></div>' + create + '<datalist id="item-materials">' + materialOptions + '</datalist></section>' : '';
        q('#workspace').innerHTML = pageHead('Items') + createPanel +
            '<div class="section-heading editor-section-heading"><div><h2>Custom items</h2><p class="tiny">Open an item in its own editor view.</p></div>' +
            '<span class="tiny">' + items.length + ' item' + (items.length === 1 ? '' : 's') + '</span></div>' +
            '<section class="card"><div class="list">' + (list || '<div class="empty">No custom items found.</div>') + '</div></section>';
        const createForm = q('#item-create');
        if (createForm) createForm.onsubmit = async event => {
            event.preventDefault();
            const values = new FormData(createForm);
            try {
                setStatus('Creating item…');
                const result = await request(apiUrl('items', 'create'), {method: 'POST', headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify(Object.fromEntries(values.entries()))});
                notice(result.message || 'Item created.');
                await openModule('items');
            } catch (error) { notice(error.message, true); setStatus('Needs attention', true); }
        };
        q('#workspace').querySelectorAll('[data-item]').forEach(button => button.onclick = () => {
            selectedItem = button.dataset.item;
            renderItems(activeData);
        });
    }


    function renderItemForm(item) {
        if (!item) return;
        const canWrite = Boolean(moduleInfo()?.writable);
        const values = item.values || {};
        const stats = values.stats || {};
        const enchantments = values.enchantments && typeof values.enchantments === 'object' ?
            Object.entries(values.enchantments).map(([key, value]) => key + '=' + value).join(',') : '';
        const lore = Array.isArray(values.lore) ? values.lore.join('\n') : (values.lore || '');
        const save = canWrite ? '<button class="button primary" type="submit">Save item</button>' : '';
        q('#item-form').innerHTML = '<div class="document-head"><div><h3>' + esc(item.displayName) +
            '</h3><small class="muted">' + esc(item.pack) + ' / ' + esc(item.file) + '</small></div></div><form id="item-editor-form"><fieldset' +
            (canWrite ? '' : ' disabled') + '><div class="field-grid"><label>Name<input name="name" value="' +
            esc(values.name || '') + '"></label><label>Material<input name="material" value="' +
            esc(values.material || item.material) + '"></label><label>Colour<input name="color" value="' +
            esc(values.color || '') + '"></label><label>Texture<input name="texture" value="' +
            esc(values.texture || '') + '"></label><label>Durability<input name="durability" type="number" min="1" value="' +
            esc(values['max-damage'] || '') + '"></label><label>Attack damage<input name="attack-damage" type="number" step="any" value="' +
            esc(stats['attack-damage'] || '') + '"></label><label>Attack speed<input name="attack-speed" type="number" step="any" value="' +
            esc(stats['attack-speed'] || '') + '"></label><label>Armour<input name="armor" type="number" step="any" value="' +
            esc(stats.armor || '') + '"></label><label>Armour toughness<input name="toughness" type="number" step="any" value="' +
            esc(stats.toughness || '') + '"></label><label class="wide">Lore<textarea class="small-area" name="lore">' +
            esc(lore) + '</textarea></label><label class="wide">Enchantments<input name="enchantments" value="' +
            esc(enchantments) + '" placeholder="sharpness=2,unbreaking=1"></label></div><details><summary>Abilities JSON</summary>' +
            '<textarea class="small-area" name="abilities">' + esc(JSON.stringify(values.abilities || {}, null, 2)) +
            '</textarea></details><div class="card-actions">' + save +
            '</div></fieldset></form>';
        q('#item-editor-form').onsubmit = async event => {
            event.preventDefault();
            if (!canWrite) return;
            const form = new FormData(event.target);
            const fields = Object.fromEntries(['name','material','color','texture','durability','attack-damage','attack-speed','armor','toughness','lore','enchantments']
                .map(key => [key, form.get(key) || '']));
            try { fields.abilities = JSON.parse(form.get('abilities') || '{}'); }
            catch (error) { notice('Abilities must be valid JSON.', true); return; }
            try {
                setStatus('Saving item…');
                const result = await request(apiUrl('items', 'save'), {method: 'POST', headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({id: item.id, revision: item.revision, fields})});
                notice(result.message || 'Item saved.');
                await openModule('items');
            } catch (error) { notice(error.message, true); setStatus('Needs attention', true); }
        };
    }

    function renderLoot(data) {
        const writable = Boolean(moduleInfo()?.writable);
        const definitions = data.definitions || [];
        if (selectedLoot && !definitions.some(definition => lootKey(definition) === selectedLoot)) selectedLoot = null;
        const selected = definitions.find(definition => lootKey(definition) === selectedLoot);
        const defaultPack = (data.packs || []).find(pack => pack.enabled)?.id || data.packs?.[0]?.id || '';
        const packs = (data.packs || []).map(pack => '<option value="' + esc(pack.id) + '"' +
            (pack.id === defaultPack ? ' selected' : '') + '>' + esc(pack.id) + (pack.enabled ? '' : ' (disabled)') + '</option>').join('');
        const files = (data.documents || []).map(document => '<option value="' + esc(document.pack + '|' + document.file) + '">' +
            esc(document.pack + ' / ' + document.file) + '</option>').join('');
        const items = (data.items || []).map(item => '<option value="' + esc(item) + '"></option>').join('');
        const blocks = (data.blocks || []).map(block => '<option value="' + esc(block) + '"></option>').join('');
        const tableList = definitions.map(definition => '<button class="list-button" type="button" data-loot-table="' +
            esc(lootKey(definition)) + '"><strong>' + esc(definition.id) + '</strong><small>' +
            esc(definition.pack + ' / ' + definition.file) + '</small></button>').join('');
        const createForm = writable ? '<form id="loot-create"><div class="field-grid"><label>Pack<select name="pack">' +
            packs + '</select></label><label>File<input name="file" list="loot-files" value="configs/loot.yml"></label>' +
            '<label>Table ID<input name="table" placeholder="tree-drops" pattern="[a-z0-9][a-z0-9-]*" required></label>' +
            '<label>Trigger<select name="target\"><option value="block-break">Block broken</option><option value="mob-death">Mob defeated</option></select></label>' +
            '<label id="loot-block-field" class="wide">Block type(s)<input name="block" list="block-types" placeholder="OAK_LOG, BIRCH_LOG"></label>' +
            '<label id="loot-entity-field" class="wide hidden">Entity type<input name="entity" placeholder="ZOMBIE"></label>' +
            '<label class="wide">Drop item<input name="item" list="loot-items" placeholder="DIAMOND or pack:item-id" required></label>' +
            '<label>Chance<input name="chance" type="number" min="0" max="1" step="0.01" value="1"></label>' +
            '<label>Minimum amount<input name="min" type="number" min="1" value="1"></label><label>Maximum amount<input name="max" type="number" min="1" value="1"></label>' +
            '<label>World (optional)<input name="world" placeholder="survival"></label><label>Biome (optional)<input name="biome" placeholder="minecraft:forest"></label>' +
            '</div><div class="card-actions"><button class="button primary" type="submit">Add loot rule</button></div></form>' : '';
        const createPanel = writable ? '<details class="card disclosure" id="loot-create-panel"><summary>' +
            iconSvg(moduleIcons.default, 'section-icon') + '<span>Add loot rule</span></summary><div class="disclosure-content">' + createForm +
            '<datalist id="loot-files">' + files + '</datalist><datalist id="loot-items">' + items +
            '</datalist><datalist id="block-types">' + blocks + '</datalist></div></details>' : '';
        const simulation = '<form id="loot-sim"><div class="field-grid"><label>Table<select name="table">' +
            (data.tables || []).map(table => '<option value="' + esc(table) + '">' + esc(table) + '</option>').join('') +
            '</select></label><label>Rolls<input name="rolls" type="number" min="1" max="100000" value="10"></label>' +
            '<label>Mob (optional)<input name="entity" placeholder="ZOMBIE"></label><label>Block (optional)<input name="block" placeholder="OAK_LOG"></label>' +
            '<label>Biome (optional)<input name="biome" placeholder="minecraft:forest"></label><label>World (optional)<input name="world" placeholder="survival"></label>' +
            '</div><div class="card-actions"><button class="button" type="submit">Run test</button></div></form>';
        if (selected) {
            const source = '<details class="card disclosure" id="loot-source-panel"><summary><span>Open source file</span></summary><div class="disclosure-content"><div class="card-actions source-picker">' +
                '<select id="loot-doc"><option value="">Choose a file</option>' + files + '</select><button id="loot-doc-open" class="button small primary" type="button">Open file</button></div></div></details>';
            q('#workspace').innerHTML = subviewHead(selected.id) +
                '<div class="stack"><section class="card\"><div id="loot-summary">' + renderLootSummary(selected) + '</div></section>' +
                '<details class="card disclosure" id="loot-test-panel"><summary><span>Test drops</span></summary><div class="disclosure-content">' + simulation +
                '<div id="loot-result" class="sim-result"></div></div></details>' + source + '</div>';
            bindLootForm();
            bindDocumentPicker('loot', 'loot-doc', 'loot-doc-open');
            q('[data-subview-back]').onclick = () => { selectedLoot = null; renderLoot(activeData); };
            return;
        }
        q('#workspace').innerHTML = pageHead('Loot') +
            '<div class="section-heading editor-section-heading"><div><h2>Loot tables</h2><p class="tiny">Open a table to review its rules and drops.</p></div>' +
            '<span class="tiny">' + definitions.length + ' table' + (definitions.length === 1 ? '' : 's') + '</span></div>' +
            '<section class="card\"><div class="list">' + (tableList || '<div class="empty">No loot tables found.</div>') + '</div></section>' +
            '<div class="loot-tools">' + createPanel + '<details class="card disclosure" id="loot-test-panel"><summary><span>Test drops</span></summary><div class="disclosure-content">' + simulation +
            '<div id="loot-result" class="sim-result"></div></div></details></div>';
        bindLootForm();
        q('#workspace').querySelectorAll('[data-loot-table]').forEach(button => button.onclick = () => {
            selectedLoot = button.dataset.lootTable;
            renderLoot(activeData);
        });
    }


    function lootKey(definition) { return definition.pack + '|' + definition.file + '|' + definition.id; }

    function renderLootSummary(definition) {
        const table = definition.table || {};
        const rules = Array.isArray(table.rules) ? table.rules : [];
        const rows = rules.map(rule => {
            const conditions = rule.conditions && typeof rule.conditions === 'object' ?
                Object.entries(rule.conditions).map(([key, value]) => key + ': ' + (Array.isArray(value) ? value.join(', ') : value)).join(' · ') :
                'No conditions';
            const entries = Array.isArray(rule.entries) ? rule.entries : [];
            const drops = entries.map(entry => entry.item + ' × ' + (entry.min ?? 1) + '-' + (entry.max ?? entry.min ?? 1) +
                ' (' + ((Number(entry.chance ?? 1) * 100).toFixed(0)) + '%)').join(', ');
            const operation = rule.operation && rule.operation !== 'APPEND' ? ' · ' + esc(rule.operation) : '';
            return '<div class="rule-summary"><div class="rule-summary-head"><strong>' + esc(drops || 'No drops') +
                '</strong><span class="tiny">Drop' + operation + '</span></div><div><span class="rule-condition-label">When</span> ' +
                esc(conditions && conditions !== 'No conditions' ? conditions : 'Always') + '</div></div>';
        }).join('');
        const target = String(table.target || 'MOB_DEATH').toLowerCase().replace(/[_-]+/g, ' ');
        return '<div class="document-head"><div><strong>' + esc(definition.id) + '</strong><small class="muted">' +
            esc(definition.pack + ' / ' + definition.file) + '</small><div class="loot-trigger"><span>Trigger</span> ' + esc(target) + '</div></div>' +
            '<button class="button small" type="button" data-summary-doc="' + esc(definition.pack + '|' + definition.file) + '">Open source</button></div>' +
            '<div class="table-summary">' + (rows || '<div class="empty">This table has no rules.</div>') + '</div>';
    }

    function bindLootForm() {
        const form = q('#loot-create');
        if (form) {
            const target = form.elements.target;
            const block = q('#loot-block-field');
            const entity = q('#loot-entity-field');
            const updateTrigger = () => {
                const isBlock = target.value === 'block-break';
                block.classList.toggle('hidden', !isBlock);
                entity.classList.toggle('hidden', isBlock);
            };
            target.onchange = updateTrigger;
            updateTrigger();
            form.onsubmit = async event => {
                event.preventDefault();
                const values = new FormData(form);
                try {
                    setStatus('Saving loot rule…');
                    const result = await request(apiUrl('loot', 'create'), {method: 'POST', headers: {'Content-Type': 'application/json'},
                        body: JSON.stringify(Object.fromEntries(values.entries()))});
                    notice(result.message || 'Loot rule saved.');
                    await openModule('loot');
                } catch (error) { notice(error.message, true); setStatus('Needs attention', true); }
            };
        }
        const simulation = q('#loot-sim');
        if (simulation) simulation.onsubmit = async event => {
            event.preventDefault();
            const values = new FormData(simulation);
            try {
                const result = await request(apiUrl('loot', 'simulate'), {method: 'POST', headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({table: values.get('table'), rolls: Number(values.get('rolls')), entity: values.get('entity'),
                        block: values.get('block'), biome: values.get('biome'), world: values.get('world'), tier: 0, playerKill: false})});
                q('#loot-result').innerHTML = result.drops?.length ? result.drops.map(drop =>
                    '<div class="sim-drop"><span>' + esc(drop.item) + '</span><strong>×' + esc(drop.amount) +
                    '</strong></div>').join('') : '<span class="muted">No drops.</span>';
            } catch (error) { q('#loot-result').innerHTML = '<span class="status bad">' + esc(error.message) + '</span>'; }
        };
        q('#workspace').querySelectorAll('[data-summary-doc]').forEach(button => button.onclick = () => {
            const select = q('#loot-doc');
            const option = [...select.options].find(value => value.value === button.dataset.summaryDoc);
            if (option) {
                const sourcePanel = q('#loot-source-panel');
                if (sourcePanel) sourcePanel.open = true;
                select.value = option.value;
                openSelectedDocument('loot', 'loot-doc');
            }
        });
    }

    function bindDocumentPicker(module, selectId, buttonId) {
        const button = q('#' + buttonId);
        if (button) button.onclick = () => openSelectedDocument(module, selectId);
    }

    function openSelectedDocument(module, selectId) {
        const value = q('#' + selectId)?.value || '';
        if (!value) return notice('Choose a file first.');
        const separator = value.indexOf('|');
        const pack = separator >= 0 ? value.slice(0, separator) : '';
        const file = separator >= 0 ? value.slice(separator + 1) : value;
        documentReturn = module === 'packs'
            ? () => openModule(activeId, activeQuery)
            : () => renderLoot(activeData);
        q('#workspace').innerHTML = subviewHead([pack, file].filter(Boolean).join(' / ')) +
            '<section class="card"><div id="document-panel"></div></section>';
        q('[data-subview-back]').onclick = () => {
            currentDocument = null;
            const returnTo = documentReturn;
            documentReturn = null;
            if (returnTo) returnTo();
        };
        openDocument(module, pack, file);
    }

    async function openDocument(module, pack, file) {
        try {
            setStatus('Opening file…');
            if (!q('#document-panel')) throw new Error('Document view is not available.');
            q('#document-panel').innerHTML = loading('Opening file…');
            const document = await request(apiUrl(module, 'document') + '?pack=' + encodeURIComponent(pack) + '&file=' + encodeURIComponent(file));
            currentDocument = {module, pack, file, revision: document.revision, content: document.content, dirty: false};
            const canWrite = Boolean(modules.find(value => value.id === module)?.writable);
            const save = canWrite ? '<button id="document-save" class="button primary" type="button">Save</button>' : '';
            const documentLabel = [pack, file].filter(Boolean).join(' / ');
            q('#document-panel').innerHTML = '<div class="document-head"><div><strong>' + esc(documentLabel) +
                '</strong><small class="muted">Revision ' + esc(document.revision) + '</small></div>' + save +
                '</div><textarea id="document-text" spellcheck="false"' + (canWrite ? '' : ' readonly') + '>' +
                esc(document.content) + '</textarea>';
            if (canWrite) {
                q('#document-save').onclick = saveDocument;
                q('#document-text').addEventListener('input', () => {
                    if (!currentDocument) return;
                    currentDocument.dirty = q('#document-text').value !== currentDocument.content;
                });
            }
            setStatus('Ready');
        } catch (error) { notice(error.message, true); setStatus('Could not open', true); }
    }

    async function saveDocument() {
        if (!currentDocument) return;
        try {
            setStatus('Saving file…');
            const result = await request(apiUrl(currentDocument.module, 'document') + '?pack=' +
                encodeURIComponent(currentDocument.pack) + '&file=' + encodeURIComponent(currentDocument.file), {
                    method: 'PUT', headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({content: q('#document-text').value, revision: currentDocument.revision})
                });
            currentDocument.revision = result.revision;
            currentDocument.content = q('#document-text').value;
            currentDocument.dirty = false;
            notice(result.message || 'File saved.');
            setStatus('Ready');
            activeData = await loadModuleData(activeId, activeQuery);
        } catch (error) {
            if (/conflict|changed elsewhere|revision/i.test(error.message || '')) {
                draftConflictNotice(error.message + ' Your draft is still preserved.');
            } else {
                notice(error.message, true);
            }
            setStatus('Needs attention', true);
        }
    }

    function connectConfigEvents() {
        if (!window.EventSource || configEvents) return;
        configEvents = new EventSource(base + '/api/events');
        configEvents.addEventListener('config.change', event => {
            let change;
            try { change = JSON.parse(event.data); } catch (ignored) { return; }
            if (currentDocument?.dirty) {
                draftConflictNotice('The configuration changed on the server. Your unsaved draft was preserved.');
                return;
            }
            if (currentDocument) {
                openDocument(currentDocument.module, currentDocument.pack, currentDocument.file);
                return;
            }
            if (activeId) {
                loadModuleData(activeId, activeQuery).then(data => {
                    activeData = data;
                    renderModule();
                }).catch(() => { });
            }
        });
        configEvents.onerror = () => {
            if (!configEvents) return;
            configEvents.close();
            configEvents = null;
            if (configEventsRetry) clearTimeout(configEventsRetry);
            configEventsRetry = setTimeout(() => {
                configEventsRetry = null;
                connectConfigEvents();
            }, 5000);
        };
    }

    async function boot() {
        try {
            modules = await request(base + '/api/modules');
            if (!modules.length) throw new Error('No editors are available for this link.');
            activeId = modules[0].id;
            renderNav();
            await openModule(activeId);
            connectConfigEvents();
        } catch (error) {
            q('#workspace').innerHTML = '<div class="empty">' + esc(error.message) + '</div>';
            setStatus('Could not start', true);
        }
    }
    boot();
})();
