(() => {
    const base = location.pathname.replace(/\/$/, '');
    const q = selector => document.querySelector(selector);
    const esc = value => String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
    const apiUrl = (module, endpoint) => base + '/api/' + module + '/' + (endpoint || 'data');
    const clone = value => value == null ? value : JSON.parse(JSON.stringify(value));

    let modules = [];
    let activeId = null;
    let activeData = null;
    let activeQuery = {};
    let configEvents = null;
    let viewActionSequence = 0;
    let viewActions = new Map();
    let viewDraftDirty = false;

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

    function notice(message, bad = false) {
        const element = q('#notice');
        element.textContent = message;
        element.className = 'notice ' + (message ? 'show ' : '') + (bad ? 'bad' : '');
    }

    function setStatus() { }

    function loading(message) {
        return '<div class="loading" role="status"><span class="loading-spinner" aria-hidden="true"></span><span>' + esc(message) + '</span></div>';
    }

    function draftConflictNotice(message) {
        const element = q('#notice');
        element.className = 'notice show bad';
        element.innerHTML = '<div class="notice-content"><span>' + esc(message) + '</span>' +
            '<span class="notice-actions"><button id="draft-reload" class="button small primary" type="button">Reload</button>' +
            '<button id="draft-dismiss" class="button small" type="button">Keep draft</button></span></div>';
        q('#draft-reload').onclick = () => {
            notice('Reloading the latest configuration…');
            if (activeId) openModule(activeId, activeQuery);
        };
        q('#draft-dismiss').onclick = () => notice('');
    }

    function moduleInfo() { return modules.find(module => module.id === activeId); }

    function querySuffix(query) {
        const params = new URLSearchParams(Object.entries(query || {}).filter(([, value]) => value !== undefined && value !== null && value !== ''));
        const value = params.toString();
        return value ? '?' + value : '';
    }

    async function loadModuleData(id, query = {}) {
        return request(apiUrl(id, 'view') + querySuffix(query));
    }

    const ICON_PATHS = {
        grid: 'M3 3h7v7H3z M14 3h7v7h-7z M14 14h7v7h-7z M3 14h7v7H3z',
        package: 'm12 3 9 5-9 5-9-5 9-5Z M3 8v9l9 5 9-5V8 M12 13v9 M7.5 5.5l9 5',
        scroll: 'M8 4h11v16H7a3 3 0 0 1-3-3V6a2 2 0 0 1 2-2h2Zm0 0v13a3 3 0 0 0 3 3 M11 8h5 M11 12h5',
        users: 'M16 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 M10 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm7-7.8a4 4 0 0 1 0 7.6 M20 21v-2a4 4 0 0 0-3-3.87',
        boxes: 'M3 7 12 3l9 4-9 4-9-4Zm0 0v10l9 4 9-4V7 M12 11v10 M7.5 5l9 4',
        cube: 'm12 3 9 5v8l-9 5-9-5V8l9-5Zm0 0v18 M3 8l9 5 9-5',
        components: 'M4 4h6v6H4z M14 4h6v6h-6z M4 14h6v6H4z M14 14h6v6h-6z',
        tree: 'M12 3 5 10h4l-5 6h6v5h4v-5h6l-5-6h4l-7-7Z',
        circle: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Z'
    };

    function iconSvg(icon = 'grid') {
        const path = ICON_PATHS[icon];
        if (!path) return esc(icon || '•');
        return '<svg class="ui-icon module-icon-svg" viewBox="0 0 24 24" focusable="false" aria-hidden="true"><path d="' + path + '"></path></svg>';
    }

    function renderNav() {
        q('#moduleNav').innerHTML = modules.map(module => {
            const active = module.id === activeId;
            const items = active && Array.isArray(activeData?.sidebarItems) ? activeData.sidebarItems : [];
            const submenu = items.length ? '<div class="module-subnav">' + items.map(item => {
                const actionKey = registerViewAction(item.action);
                return '<button class="module-subnav-link' + (item.active ? ' active' : '') + '" type="button" data-view-action="' +
                    esc(actionKey) + '"><span class="module-subnav-icon" aria-hidden="true">' + iconSvg(item.icon) +
                    '</span><span>' + esc(item.label) + '</span></button>';
            }).join('') + '</div>' : '';
            return '<div class="module-nav-group"><button class="module-nav-link' + (active ? ' active' : '') + '" data-module="' + esc(module.id) +
                '" type="button"><span class="module-icon" aria-hidden="true">' + iconSvg(module.icon) +
                '</span><span>' + esc(module.title) + '</span></button>' + submenu + '</div>';
        }).join('');
        q('#moduleNav').querySelectorAll('[data-module]').forEach(button => {
            button.onclick = () => { setSidebar(false); openModule(button.dataset.module); };
        });
    }

    async function openModule(id, query = {}) {
        activeId = id;
        activeQuery = {...query};
        activeData = null;
        viewDraftDirty = false;
        renderNav();
        q('#topbarActions').replaceChildren();
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
        q('#workspace').innerHTML = '<div class="empty">This editor has not supplied a WebView.</div>';
    }

    function viewActionButton(action, extraClass = '') {
        if (!action) return '';
        const key = registerViewAction(action);
        const variant = ['primary', 'good', 'warn', 'danger'].includes(action.variant) ? ' ' + esc(action.variant) : '';
        return '<button class="button small' + variant + (extraClass ? ' ' + extraClass : '') + '" type="button" data-view-action="' +
            esc(key) + '">' + esc(action.label) + '</button>';
    }

    function registerViewAction(action) {
        const key = 'view-action-' + (++viewActionSequence);
        viewActions.set(key, action);
        return key;
    }

    function viewBadges(badges) {
        return (Array.isArray(badges) ? badges : []).map(badge =>
            '<span class="pill ' + (['good', 'bad', 'warn'].includes(badge.tone) ? esc(badge.tone) : '') + '">' + esc(badge.text) + '</span>').join('');
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

    function pathParts(path) { return String(path || '').split('.').filter(Boolean); }

    function getPath(root, path) {
        return pathParts(path).reduce((value, key) => value == null ? undefined : value[key], root);
    }

    function setPath(root, path, value) {
        const parts = pathParts(path);
        let parent = root;
        for (let index = 0; index < parts.length - 1; index++) {
            const part = parts[index];
            const nextIsIndex = /^\d+$/.test(parts[index + 1]);
            if (parent[part] == null) parent[part] = nextIsIndex ? [] : {};
            parent = parent[part];
        }
        if (parts.length) parent[parts[parts.length - 1]] = value;
    }

    function fieldValue(field, stateBound) {
        let value = field.value;
        if (stateBound && activeData?.state && field.name) value = getPath(activeData.state, field.name);
        return value;
    }

    function inputAttributes(attributes) {
        return Object.entries(attributes || {}).filter(([name]) => /^[a-zA-Z][a-zA-Z0-9_-]*$/.test(name))
            .map(([name, value]) => value === true ? ' ' + esc(name) : ' ' + esc(name) + '="' + esc(value) + '"').join('');
    }

    function viewField(field, stateBound = false, forceDisabled = false) {
        const type = field.type || 'text';
        const value = fieldValue(field, stateBound);
        const name = field.name || '';
        const common = ' name="' + esc(name) + '" data-field-type="' + esc(type) + '"' + inputAttributes(field.attributes) +
            (stateBound ? ' data-state-bound="true"' : '') + (field.required ? ' required' : '') +
            (field.pattern ? ' pattern="' + esc(field.pattern) + '"' : '');
        const disabled = forceDisabled || (stateBound && !activeData?.writable) ? ' disabled' : '';
        let control = '';
        const options = Array.isArray(field.options) ? field.options : [];
        if (type === 'checkbox-group') {
            let selected = {};
            try { selected = typeof value === 'string' ? JSON.parse(value || '{}') : (value || {}); } catch (ignored) { }
            const settings = field.attributes || {};
            const choices = options.map(option => {
                const checked = Object.prototype.hasOwnProperty.call(selected, option.value);
                const amount = checked ? selected[option.value] : '';
                const label = option.label || option.value;
                return '<div class="choice-row" data-choice-search="' + esc(label.toLowerCase()) + '">' +
                    '<label class="choice-label"><input type="checkbox" data-choice-value="' + esc(option.value) + '"' + (checked ? ' checked' : '') +
                    (disabled ? ' disabled' : '') + '><span>' + esc(label) + '</span></label>' +
                    '<span class="choice-amount"><small>' + esc(settings['$amount-label'] || 'Value') + '</small><input type="number" data-choice-amount="' +
                    esc(option.value) + '" aria-label="' + esc((settings['$amount-label'] || 'Value') + ' for ' + label) + '" value="' + esc(amount) + '" min="' + esc(settings.$min || '0') + '"' +
                    (settings.$max ? ' max="' + esc(settings.$max) + '"' : '') + ' step="' + esc(settings.$step || '1') + '"' +
                    (checked ? '' : ' disabled') + (disabled ? ' disabled' : '') + '></span></div>';
            }).join('');
            const filter = options.length > 12 ? '<input class="choice-search" type="search" placeholder="Search ' +
                esc(field.label.toLowerCase()) + '" data-choice-search-input' + disabled + '>' : '';
            control = '<input type="hidden"' + common + ' data-checkbox-group><div class="choice-group">' + filter +
                (choices || '<span class="muted">No choices are available.</span>') + '</div>';
            const help = field.help ? '<small class="field-help">' + esc(field.help) + '</small>' : '';
            return '<div class="webview-field' + (field.wide ? ' wide' : '') + '"><span class="field-title">' + esc(field.label) +
                '</span>' + control + help + '</div>';
        } else if (type === 'select' || type === 'multiselect') {
            const selected = type === 'multiselect' ? new Set(Array.isArray(value) ? value.map(String) : String(value || '').split('|')) : null;
            if (type === 'select' && options.length > 12) {
                const listId = 'view-suggestions-' + (++viewActionSequence);
                control = '<input' + common + ' list="' + listId + '" type="text" value="' + esc(value || '') + '" placeholder="Search or choose"' +
                    (disabled ? ' disabled' : '') + '><datalist id="' + listId + '">' + options.map(option => '<option value="' +
                    esc(option.value) + '">' + esc(option.label) + '</option>').join('') + '</datalist>';
            } else if (type === 'multiselect' && options.length > 12) {
                const choices = options.map(option => '<label class="multi-choice-row" data-choice-search="' +
                    esc((option.label || option.value).toLowerCase()) + '"><input type="checkbox" data-multi-value="' + esc(option.value) + '"' +
                    (selected.has(String(option.value)) ? ' checked' : '') + (disabled ? ' disabled' : '') + '><span>' +
                    esc(option.label || option.value) + '</span></label>').join('');
                const filter = '<input class="choice-search" type="search" placeholder="Search ' + esc(field.label.toLowerCase()) +
                    '" data-choice-search-input' + disabled + '>';
                control = '<input type="hidden"' + common + ' data-multiselect-group><div class="multi-choice-group">' + filter + choices + '</div>';
            } else control = '<select' + common + (disabled ? ' disabled' : '') + (type === 'multiselect' ? ' multiple' : '') + '>' +
                options.map(option => {
                    const isSelected = type === 'multiselect' ? selected.has(String(option.value)) : String(option.value) === String(value ?? '');
                    return '<option value="' + esc(option.value) + '"' + (isSelected ? ' selected' : '') + '>' + esc(option.label) + '</option>';
                }).join('') + '</select>';
        } else if (type === 'textarea' || type === 'lines' || type === 'json') {
            let text = value;
            if (type === 'lines' && Array.isArray(text)) text = text.join('\n');
            if (type === 'json' && typeof text !== 'string') text = JSON.stringify(text ?? {}, null, 2);
            control = '<textarea class="' + (type === 'json' ? 'webview-json' : 'small-area') + '"' + common +
                (disabled ? ' disabled' : '') + ' placeholder="' + esc(field.placeholder || '') + '" spellcheck="' +
                (type === 'json' ? 'false' : 'true') + '">' + esc(text || '') + '</textarea>';
        } else if (type === 'checkbox') {
            const checked = value === true || value === 'true';
            control = '<input' + common + ' type="checkbox"' + (checked ? ' checked' : '') + (disabled ? ' disabled' : '') + '><span class="switch-track" aria-hidden="true"></span>';
            return '<div><label class="switch-control' + (field.wide ? ' wide' : '') + '"><span>' + esc(field.label) + '</span>' + control +
                '</label>' + (field.help ? '<small class="field-help">' + esc(field.help) + '</small>' : '') + '</div>';
        } else if (type === 'suggestion') {
            const listId = 'view-suggestions-' + (++viewActionSequence);
            control = '<input' + common + ' list="' + listId + '" type="text" value="' + esc(value || '') + '" placeholder="' +
                esc(field.placeholder || '') + '"' + (disabled ? ' disabled' : '') + '><datalist id="' + listId + '">' +
                (Array.isArray(field.options) ? field.options : []).map(option => '<option value="' + esc(option.value) + '">' +
                    esc(option.label) + '</option>').join('') + '</datalist>';
        } else {
            control = '<input' + common + ' type="' + esc(type) + '" value="' + esc(value ?? '') + '" placeholder="' +
                esc(field.placeholder || '') + '"' + (disabled ? ' disabled' : '') + '>';
        }
        const link = field.link ? '<button class="field-record-link" type="button" data-field-link-module="' + esc(field.link.module) +
            '" data-field-link-parameter="' + esc(field.link.parameter) + '" data-field-link-query="' +
            esc(JSON.stringify(field.link.query || {})) + '" data-field-link-prefix="' + esc(field.link.valuePrefix || '') +
            '" title="' + esc(field.link.label || 'Open related record') +
            '" aria-label="' + esc(field.link.label || 'Open related record') + '">…</button>' : '';
        const help = field.help ? '<small class="field-help">' + esc(field.help) + '</small>' : '';
        return '<label class="webview-field' + (field.wide ? ' wide' : '') + '"><span class="field-title">' + esc(field.label) +
            '</span><span class="field-control' + (link ? ' has-record-link' : '') + '">' + control + link + '</span>' + help + '</label>';
    }

    function fieldGrid(fields, columns = 2, stateBound = false, forceDisabled = false) {
        return '<div class="field-grid webview-grid" style="--webview-columns:' + boundedColumns(columns) + '">' +
            (Array.isArray(fields) ? fields.map(field => viewField(field, stateBound, forceDisabled)).join('') : '') + '</div>';
    }

    function boundedColumns(value) {
        const columns = Number(value);
        return Number.isInteger(columns) ? Math.max(1, Math.min(6, columns)) : 1;
    }

    function fieldInputValue(element) {
        const type = element.dataset.fieldType || element.type;
        if (type === 'checkbox') return element.checked;
        if (type === 'multiselect') {
            const group = element.closest('[data-multiselect-group]')?.parentElement;
            if (group) return [...group.querySelectorAll('[data-multi-value]:checked')].map(choice => choice.dataset.multiValue);
            return [...element.selectedOptions].map(option => option.value);
        }
        if (type === 'checkbox-group') {
            const group = element.closest('[data-checkbox-group]')?.parentElement;
            const result = {};
            group?.querySelectorAll('[data-choice-value]:checked').forEach(choice => {
                const amount = group.querySelector('[data-choice-amount="' + CSS.escape(choice.dataset.choiceValue) + '"]');
                result[choice.dataset.choiceValue] = amount?.value === '' ? 1 : Number(amount?.value || 1);
            });
            return result;
        }
        if (type === 'lines') return String(element.value).split(/\r?\n/).map(line => line.trim()).filter(Boolean);
        if (type === 'number' || type === 'range') return element.value === '' ? null : Number(element.value);
        if (type === 'json') {
            try { return JSON.parse(element.value || '{}'); }
            catch (error) { throw new Error('Enter valid JSON in “' + (element.closest('label')?.childNodes?.[0]?.textContent?.trim() || element.name) + '”.'); }
        }
        return element.value;
    }

    function openEditorDialog(title, body, submitLabel, onSubmit) {
        const dialog = q('#editor-dialog');
        dialog.innerHTML = '<form id="editor-dialog-form"><div class="dialog-head"><h2>' + esc(title) +
            '</h2><button class="button dialog-close" type="button" data-dialog-close aria-label="Close">×</button></div>' + body +
            '<div class="dialog-actions"><button class="button" type="button" data-dialog-close>Cancel</button><button class="button primary" type="submit">' +
            esc(submitLabel) + '</button></div></form>';
        dialog.querySelectorAll('[data-dialog-close]').forEach(button => button.onclick = () => dialog.close());
        dialog.querySelector('#editor-dialog-form').onsubmit = async event => {
            event.preventDefault();
            await onSubmit(event.target, dialog);
        };
        dialog.showModal();
    }

    function openDeclarativeDialog(action) {
        const dialog = action.dialog || {};
        const body = fieldGrid(dialog.fields || [], dialog.columns || 2, false);
        openEditorDialog(dialog.title, body, dialog.submitLabel, async (form, element) => {
            try {
                const payload = {...(action.payload || {})};
                for (const control of form.elements) {
                    if (!control.name) continue;
                    payload[control.name] = fieldInputValue(control);
                }
                if (!action.endpoint) {
                    element.close();
                    notice('Dialog submitted.');
                    return;
                }
                const result = await request(apiUrl(activeId, action.endpoint), {
                    method: action.method || 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(payload)
                });
                element.close();
                await openModule(activeId, activeQuery);
                notice(result.message || 'Saved.');
            } catch (error) { notice(error.message, true); }
        });
    }

    function renderViewResult(result, definition) {
        const values = Array.isArray(result?.[definition.items]) ? result[definition.items] : [];
        if (!values.length) return '<span class="muted">' + esc(definition.empty || 'No results.') + '</span>';
        return values.map(value => '<div class="sim-drop"><span>' + esc(value?.[definition.label] ?? '') +
            '</span><strong>' + esc(value?.[definition.value] ?? '') + '</strong></div>').join('');
    }

    async function dispatchViewRequest(action, payload, refresh = true) {
        if (!action || !action.endpoint) return;
        if (action.confirmation && !window.confirm(action.confirmation)) return;
        try {
            setStatus('Working…');
            const result = await request(apiUrl(activeId, action.endpoint), {
                method: action.method || 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(payload || {})
            });
            if (action.result?.target) {
                const target = q('#' + action.result.target);
                if (target) target.innerHTML = renderViewResult(result, action.result);
            }
            if (refresh && action.result?.refresh !== false) await openModule(activeId, action.result?.query || activeQuery);
            if (result.message) notice(result.message);
            setStatus('Ready');
        } catch (error) {
            if (/conflict|changed elsewhere|revision/i.test(error.message || ''))
                draftConflictNotice(error.message + ' Your unsaved edits are still preserved.');
            else notice(error.message, true);
            setStatus('Needs attention', true);
        }
    }

    async function dispatchViewForm(form, action) {
        if (!action || action.type === 'display') return;
        try {
            let payload;
            if (form.dataset.submitState === 'true') {
                payload = {...(action.payload || {}), ...clone(activeData?.state || {})};
                for (const element of form.elements) {
                    if (!element.name || !element.dataset.stateBound) continue;
                    setPath(payload, element.name, fieldInputValue(element));
                }
            } else {
                const fields = {};
                for (const element of form.elements) {
                    if (element.name) fields[element.name] = fieldInputValue(element);
                }
                payload = {...(action.payload || {})};
                if (form.dataset.payloadField) payload[form.dataset.payloadField] = fields;
                else Object.assign(payload, fields);
            }
            await dispatchViewRequest(action, payload, action.result?.refresh !== false);
        } catch (error) { notice(error.message, true); }
    }

    async function dispatchViewAction(action) {
        if (!action || action.type === 'display') return;
        if (action.type === 'navigate') {
            await openModule(activeId, action.query || {});
            return;
        }
        if (action.type === 'module') {
            const target = action.result?.module;
            if (target) await openModule(target, action.query || {});
            return;
        }
        if (action.type === 'dialog') {
            if (action.confirmation && !window.confirm(action.confirmation)) return;
            openDeclarativeDialog(action);
            return;
        }
        if (action.type === 'request') await dispatchViewRequest(action, action.payload || {});
    }

    function renderTabs(properties) {
        const tabs = Array.isArray(properties.tabs) ? properties.tabs : (Array.isArray(properties.items) ? properties.items : []);
        return '<div class="tabs component-tabs" role="tablist">' + tabs.map(tab => {
            const active = Boolean(tab.active) || tab.id === properties.active;
            const key = tab.action ? registerViewAction(tab.action) : '';
            return '<button class="tab' + (active ? ' active' : '') + '" type="button" role="tab" aria-selected="' +
                (active ? 'true' : 'false') + '"' + (key ? ' data-view-action="' + esc(key) + '"' : '') + '>' + esc(tab.label) + '</button>';
        }).join('') + '</div>';
    }

    function renderTreeNode(node) {
        const children = Array.isArray(node.children) ? node.children : [];
        const actionKey = registerViewAction(node.action);
        const expanded = Boolean(node.expanded);
        const searchText = [node.label, node.description].join(' ').toLowerCase();
        return '<div class="tree-node" data-tree-node data-search-text="' + esc(searchText) + '"><div class="tree-row">' +
            (children.length ? '<button class="tree-toggle" type="button" data-tree-toggle aria-expanded="' + (expanded ? 'true' : 'false') +
                '" aria-label="' + (expanded ? 'Collapse ' : 'Expand ') + esc(node.label) + '">' +
                '<svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m9 18 6-6-6-6"></path></svg></button>' :
                '<span class="tree-toggle-placeholder" aria-hidden="true"></span>') +
            '<button class="tree-link" type="button" data-view-action="' + esc(actionKey) + '"><span class="tree-link-main"><strong>' +
            esc(node.label) + '</strong>' + (node.description ? '<small>' + esc(node.description) + '</small>' : '') +
            '</span>' + viewBadges(node.badges) + '</button></div>' +
            (children.length ? '<div class="tree-children" data-tree-children data-default-expanded="' + (expanded ? 'true' : 'false') + '"' +
                (expanded ? '' : ' hidden') + '>' + children.map(renderTreeNode).join('') + '</div>' : '') + '</div>';
    }

    function renderTree(properties) {
        const nodes = Array.isArray(properties.nodes) ? properties.nodes : [];
        const search = properties.searchable ? '<input class="webview-list-search tree-search" type="search" placeholder="Search ' +
            esc(properties.label || 'items') + '" data-tree-search>' : '';
        return '<section class="webview-tree">' + (properties.label ? '<span class="component-label">' + esc(properties.label) + '</span>' : '') +
            search + '<div class="tree-list">' + (nodes.length ? nodes.map(renderTreeNode).join('') :
                '<div class="empty">' + esc(properties.empty || 'No entries.') + '</div>') + '</div></section>';
    }

    function dataPathAttribute(path) { return esc(JSON.stringify(path)); }

    function dataKeyLabel(value) {
        return String(value || '').replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/[-_]+/g, ' ')
            .replace(/\s+/g, ' ').trim().replace(/\b\w/g, character => character.toUpperCase());
    }

    function dataTypeOptions() {
        return '<option value="text">Text</option><option value="number">Number</option>' +
            '<option value="boolean">On / off</option><option value="object">Group</option><option value="list">List</option>';
    }

    function renderDataScalar(value, path, label, disabled, hideLabel) {
        const pathAttribute = dataPathAttribute(path);
        const labelMarkup = hideLabel ? '' : '<span>' + esc(label) + '</span>';
        if (typeof value === 'boolean') {
            return '<label class="switch-control data-editor-scalar' + (hideLabel ? ' no-title' : '') + '">' + labelMarkup +
                '<input type="checkbox" data-config-value data-config-path="' + pathAttribute + '" data-config-kind="boolean"' +
                (value ? ' checked' : '') + (disabled ? ' disabled' : '') + '><span class="switch-track" aria-hidden="true"></span></label>';
        }
        if (typeof value === 'number') {
            return '<label class="data-editor-scalar' + (hideLabel ? ' no-title' : '') + '">' + labelMarkup +
                '<input type="number" step="any" data-config-value data-config-path="' + pathAttribute + '" data-config-kind="number" value="' +
                esc(value) + '"' + (disabled ? ' disabled' : '') + '></label>';
        }
        const text = value == null ? '' : String(value);
        const common = ' data-config-value data-config-path="' + pathAttribute + '" data-config-kind="text"' + (disabled ? ' disabled' : '');
        const control = text.includes('\n') || text.length > 160
            ? '<textarea' + common + '>' + esc(text) + '</textarea>'
            : '<input type="text"' + common + ' value="' + esc(text) + '">';
        return '<label class="data-editor-scalar' + (hideLabel ? ' no-title' : '') + '">' + labelMarkup + control + '</label>';
    }

    function renderDataValue(value, path, label, depth, disabled, hideScalarLabel = false) {
        if (depth > 24) return '<div class="empty">This value is nested too deeply to display.</div>';
        if (Array.isArray(value)) {
            const rows = value.map((item, index) => '<article class="data-editor-array-item"><div class="data-editor-entry-head"><strong>Item ' +
                (index + 1) + '</strong><button class="button small danger" type="button" data-config-remove-index="' + index +
                '" data-config-path="' + dataPathAttribute(path) + '"' + (disabled ? ' disabled' : '') + '>Remove</button></div>' +
                renderDataValue(item, [...path, index], 'Item ' + (index + 1), depth + 1, disabled) + '</article>').join('');
            return '<details class="data-editor-node data-editor-list"' + (depth < 2 ? ' open' : '') + '><summary><span>' + esc(dataKeyLabel(label)) +
                '</span><small>' + value.length + ' item' + (value.length === 1 ? '' : 's') + '</small></summary><div class="data-editor-children">' +
                (rows || '<p class="muted">No items.</p>') + '<div class="data-editor-add"><label><span>Add item as</span><select data-config-add-type>' +
                dataTypeOptions() + '</select></label><button class="button small" type="button" data-config-add-index data-config-path="' +
                dataPathAttribute(path) + '"' + (disabled ? ' disabled' : '') + '>Add item</button></div></div></details>';
        }
        if (value && typeof value === 'object') {
            const entries = Object.entries(value);
            const rows = entries.map(([key, child]) => '<article class="data-editor-map-item"><div class="data-editor-entry-head">' +
                '<label class="data-editor-key"><span>Field name</span><input type="text" data-config-key data-config-parent="' +
                dataPathAttribute(path) + '" data-config-old-key="' + esc(key) + '" value="' + esc(key) + '"' +
                (disabled ? ' disabled' : '') + '></label><button class="button small danger" type="button" data-config-remove-key="' +
                esc(key) + '" data-config-path="' + dataPathAttribute(path) + '"' + (disabled ? ' disabled' : '') + '>Remove</button></div>' +
                '<div class="data-editor-map-value">' + renderDataValue(child, [...path, key], dataKeyLabel(key), depth + 1, disabled, true) + '</div></article>').join('');
            return '<details class="data-editor-node data-editor-object"' + (depth < 2 ? ' open' : '') + '><summary><span>' + esc(dataKeyLabel(label)) +
                '</span><small>' + entries.length + ' field' + (entries.length === 1 ? '' : 's') + '</small></summary><div class="data-editor-children">' +
                (rows || '<p class="muted">No fields.</p>') + '<div class="data-editor-add"><label class="data-editor-new-key"><span>New field name</span>' +
                '<input type="text" data-config-new-key placeholder="For example, enabled"></label><label><span>Field type</span><select data-config-add-type>' +
                dataTypeOptions() + '</select></label><button class="button small" type="button" data-config-add-key data-config-path="' +
                dataPathAttribute(path) + '"' + (disabled ? ' disabled' : '') + '>Add field</button></div></div></details>';
        }
        return renderDataScalar(value, path, label, disabled, hideScalarLabel);
    }

    function dataPathValue(root, path) {
        return Array.isArray(path) ? path.reduce((value, key) => value == null ||
            !Object.prototype.hasOwnProperty.call(value, key) ? undefined : value[key], root) : undefined;
    }

    function setDataPath(root, path, value) {
        if (!Array.isArray(path) || !path.length) return;
        let parent = root;
        for (let index = 0; index < path.length - 1; index++) {
            if (parent == null) return;
            parent = parent[path[index]];
        }
        if (parent == null || typeof parent !== 'object') return;
        const key = path[path.length - 1];
        if (Array.isArray(parent)) parent[key] = value;
        else Object.defineProperty(parent, key, {value, writable: true, enumerable: true, configurable: true});
    }

    function defaultDataValue(type) {
        if (type === 'number') return 0;
        if (type === 'boolean') return false;
        if (type === 'object') return {};
        if (type === 'list') return [];
        return '';
    }

    function renderDataEditor(properties, disabled) {
        const path = properties.path || 'data';
        const data = getPath(activeData?.state, path);
        return '<section class="webview-data-editor" style="--data-editor-columns:' + boundedColumns(properties.columns || 2) + '">' +
            renderDataValue(data, path.split('.'), properties.label || 'Configuration', 0, disabled || !activeData?.writable) + '</section>';
    }

    function renderDeclarativeComponent(component, context = {}) {
        const type = component.type;
        const properties = component.properties || {};
        const stateBound = Boolean(context.stateBound);
        if (type === 'panel') {
            const children = Array.isArray(properties.children) ? properties.children.map(child => renderDeclarativeComponent(child, context)).join('') : '';
            const actions = Array.isArray(properties.actions) ? properties.actions.map(viewActionButton).join('') : '';
            return '<section class="card webview-panel"><div class="document-head"><div>' +
                (properties.title ? '<h3>' + esc(properties.title) + '</h3>' : '') +
                (properties.description ? '<p>' + esc(properties.description) + '</p>' : '') + '</div>' +
                (properties.meta ? '<small class="muted">' + esc(properties.meta) + '</small>' : '') + '</div>' +
                '<div class="webview-grid" style="--webview-columns:' + boundedColumns(properties.columns) + '">' + children + '</div>' +
                (actions ? '<div class="card-actions">' + actions + '</div>' : '') + '</section>';
        }
        if (type === 'cards') {
            const items = Array.isArray(properties.items) ? properties.items : [];
            if (!items.length) return '<div class="empty">' + esc(properties.empty || 'Nothing found.') + '</div>';
            return '<div class="webview-card-grid webview-grid" style="--webview-columns:' + boundedColumns(properties.columns) + '">' + items.map(item => {
                const actionKey = item.action ? registerViewAction(item.action) : '';
                return '<article class="card webview-card"' + (actionKey ? ' tabindex="0" role="button" data-view-action="' + esc(actionKey) + '"' : '') + '>' +
                    '<div class="webview-card-icon" aria-hidden="true">' + iconSvg() + '</div><div class="webview-card-main">' +
                    '<div class="webview-card-title"><h3>' + esc(item.title) + '</h3></div>' +
                    (item.description ? '<p class="webview-card-description">' + esc(item.description) + '</p>' : '') +
                    '<div class="webview-card-meta">' + viewBadges(item.badges) + '</div></div></article>';
            }).join('') + '</div>';
        }
        if (type === 'subview') {
            const key = registerViewAction(properties.back);
            return '<div class="subview-head"><button class="button subview-back" type="button" data-view-action="' + esc(key) + '">← ' +
                esc(properties.back?.label || 'Back') + '</button><h2>' + esc(properties.title || '') + '</h2></div>';
        }
        if (type === 'fields') return fieldGrid(properties.fields, properties.columns || 2, stateBound, context.disabled);
        if (type === 'data-editor') return renderDataEditor(properties, context.disabled);
        if (type === 'form') {
            const formId = properties.id || 'webview-form-' + (++viewActionSequence);
            const actions = Array.isArray(properties.actions) ? properties.actions : [];
            const formDisabled = !activeData?.writable && actions.length === 0;
            const nested = Array.isArray(properties.children) ? properties.children.map(child =>
                renderDeclarativeComponent(child, {stateBound: properties.submitState || stateBound, disabled: formDisabled || context.disabled})).join('') : '';
            return '<form class="component-form" id="' + esc(formId) + '" data-view-form="true" data-submit-state="' +
                (properties.submitState ? 'true' : 'false') + '" data-payload-field="' + esc(properties.payloadField || '') + '">' +
                fieldGrid(properties.fields, properties.columns || 2, properties.submitState || stateBound, formDisabled || context.disabled) + nested +
                (actions.length ? '<div class="card-actions">' + actions.map(action => {
                    const key = registerViewAction(action);
                    return '<button class="button' + (action.variant === 'primary' ? ' primary' : '') + '" type="submit" data-view-form-action="' +
                        esc(key) + '">' + esc(action.label) + '</button>';
                }).join('') + '</div>' : '') +
                (properties.resultId ? '<div id="' + esc(properties.resultId) + '" class="sim-result"></div>' : '') + '</form>';
        }
        if (type === 'repeater') {
            const values = getPath(activeData?.state, properties.path);
            const items = Array.isArray(values) ? values : [];
            const rows = items.map((item, index) => {
                return '<article class="card webview-repeater-item"><div class="document-head"><strong>' + esc(properties.itemLabel || 'Item') + ' ' + (index + 1) +
                    '</strong><div class="card-actions">' + (properties.reorderable ?
                    '<button class="button small" type="button" data-repeater="up" data-path="' + esc(properties.path) + '" data-index="' + index + '" aria-label="Move up">↑</button>' +
                    '<button class="button small" type="button" data-repeater="down" data-path="' + esc(properties.path) + '" data-index="' + index + '" aria-label="Move down">↓</button>' : '') +
                    '<button class="button small danger" type="button" data-repeater="remove" data-path="' + esc(properties.path) + '" data-index="' + index + '">Remove</button>' +
                    '</div></div>' + fieldGrid(properties.fields?.map(field => ({...field,
                        name: properties.path + '.' + index + '.' + field.name, value: getPath(item, field.name)})), properties.columns || 2, stateBound, context.disabled) + '</article>';
            }).join('');
            return '<section class="webview-repeater"><div class="document-head"><h4>' + esc(properties.title || '') + '</h4>' +
                '<button class="button small" type="button" data-repeater="add" data-path="' + esc(properties.path) + '" data-default="' +
                esc(JSON.stringify(properties.defaultItem || {})) + '">' + esc(properties.addLabel || 'Add item') + '</button></div>' +
                (rows || '<p class="muted">No entries.</p>') + '</section>';
        }
        if (type === 'tabs') return renderTabs(properties);
        if (type === 'tree') return renderTree(properties);
        if (type === 'list') {
            const items = Array.isArray(properties.items) ? properties.items : [];
            const search = properties.searchable ? '<input class="webview-list-search" type="search" placeholder="' + esc(properties.searchPlaceholder || 'Search') + '" data-list-search>' : '';
            const list = items.map(item => {
                const actionKey = item.action ? registerViewAction(item.action) : '';
                return actionKey ? '<button class="list-button" type="button" data-view-action="' + esc(actionKey) + '" data-search-text="' +
                    esc((item.title || '') + ' ' + (item.description || '')) + '"><strong>' + esc(item.title || '') + '</strong><small>' +
                    esc(item.description || '') + '</small>' + viewBadges(item.badges) + '</button>' :
                    '<div class="list-button" data-search-text="' + esc((item.title || '') + ' ' + (item.description || '')) + '"><strong>' +
                    esc(item.title || '') + '</strong><small>' + esc(item.description || '') + '</small>' + viewBadges(item.badges) + '</div>';
            }).join('');
            return '<div class="webview-list">' + (properties.label ? '<span class="component-label">' + esc(properties.label) + '</span>' : '') +
                search + '<div class="list" data-search-list>' + (list || '<div class="empty">' + esc(properties.empty || 'Nothing found.') + '</div>') +
                '</div></div>';
        }
        if (type === 'table') {
            const columns = Array.isArray(properties.columns) ? properties.columns : [];
            const rows = Array.isArray(properties.rows) ? properties.rows : [];
            return '<div class="table-wrap">' + (properties.label ? '<span class="component-label">' + esc(properties.label) + '</span>' : '') +
                '<table class="data-table"><thead><tr>' + columns.map(column => '<th>' + esc(column.label || column.key || '') + '</th>').join('') +
                '</tr></thead><tbody>' + rows.map(row => '<tr>' + columns.map(column => '<td>' + esc(row[column.key] ?? '') + '</td>').join('') +
                '</tr>').join('') + '</tbody></table></div>';
        }
        if (type === 'validation') {
            const errors = Array.isArray(properties.errors) ? properties.errors : [];
            return errors.length ? '<div><h4>Validation issues</h4><ul class="errors webview-validation-list">' + errors.map(formatValidationError).join('') + '</ul></div>' :
                '<p class="muted">' + esc(properties.empty || 'No validation issues found.') + '</p>';
        }
        if (type === 'meta') {
            const dependencies = Array.isArray(properties.dependencies) && properties.dependencies.length ?
                '<div><dt>Dependencies</dt><dd>' + esc(properties.dependencies.join(', ')) + '</dd></div>' : '';
            const resource = properties.resourcePackRebuildRequired ? '<div><dt>Resource pack</dt><dd>Rebuild required after changes.</dd></div>' : '';
            return dependencies || resource ? '<dl class="webview-metadata">' + dependencies + resource + '</dl>' : '';
        }
        if (type === 'document-picker') {
            const selectId = 'view-document-picker-' + (++viewActionSequence);
            const options = Array.isArray(properties.options) ? properties.options : [];
            return '<div class="card-actions"><select id="' + selectId + '"><option value="">Choose a file</option>' +
                options.map(option => '<option value="' + esc(option.value) + '">' + esc(option.label) + '</option>').join('') +
                '</select><button class="button small primary" type="button" data-view-document="' + esc(selectId) + '" data-view-document-module="' +
                esc(properties.module || activeId) + '" data-view-document-pack="' + esc(properties.pack || '') + '">Open editor</button></div>';
        }
        if (type === 'rules') {
            const rules = Array.isArray(properties.rules) ? properties.rules : [];
            return '<div class="table-summary">' + (rules.length ? rules.map(rule => '<div class="rule-summary"><div class="rule-summary-head"><strong>' +
                esc(rule.drops || 'No drops') + '</strong><span class="tiny">' + esc(rule.operation || 'Drop') + '</span></div><div><span class="rule-condition-label">When</span> ' +
                esc(rule.conditions || 'Always') + '</div></div>').join('') : '<div class="empty">No rules.</div>') + '</div>';
        }
        if (type === 'notice') return '<div class="notice show">' + esc(properties.text || '') + '</div>';
        if (type === 'loading') return loading(properties.text || 'Loading…');
        if (type === 'empty') return '<div class="empty">' + esc(properties.text || 'Nothing found.') + '</div>';
        return '';
    }

    function renderDeclarativeView(view) {
        viewActionSequence = 0;
        viewActions.clear();
        q('#pageTitle').textContent = view.title || moduleInfo()?.title || 'STEMCraft Editor';
        renderNav();
        const topbarActions = Array.isArray(view.topbarActions) ? view.topbarActions : [];
        q('#topbarActions').innerHTML = topbarActions.map(action => viewActionButton(action, 'topbar-action')).join('');
        q('#workspace').innerHTML = '<div class="stack">' + (view.components || []).map(component => renderDeclarativeComponent(component)).join('') + '</div>';
        bindDeclarativeActions();
    }

    function bindDeclarativeActions() {
        document.querySelectorAll('#moduleNav [data-view-action], #topbarActions [data-view-action], #workspace [data-view-action]').forEach(element => {
            const action = viewActions.get(element.dataset.viewAction);
            element.onclick = event => {
                event.stopPropagation();
                if (element.closest('#moduleNav')) setSidebar(false);
                dispatchViewAction(action);
            };
            element.onkeydown = event => {
                if ((event.key === 'Enter' || event.key === ' ') && element.getAttribute('role') === 'button') {
                    event.preventDefault(); element.click();
                }
            };
        });
        q('#workspace').querySelectorAll('[data-view-document]').forEach(button => button.onclick = () => {
            const select = q('#' + button.dataset.viewDocument);
            const file = select?.value || '';
            if (!file) return notice('Choose a file first.');
            const module = button.dataset.viewDocumentModule;
            const query = {pack: button.dataset.viewDocumentPack, file};
            if (module === activeId && activeQuery.table) query.table = activeQuery.table;
            openModule(module, query);
        });
        q('#workspace').querySelectorAll('[data-view-form]').forEach(form => form.onsubmit = event => {
            event.preventDefault();
            const submitter = event.submitter || form.querySelector('[data-view-form-action]');
            dispatchViewForm(form, viewActions.get(submitter?.dataset.viewFormAction));
        });
        q('#workspace').querySelectorAll('[data-field-link-module]').forEach(button => button.onclick = () => {
            const field = button.closest('.webview-field');
            const input = field?.querySelector('input[name],select[name],textarea[name]');
            let value = input?.value?.trim();
            if (!value) return notice('Choose or enter a linked record first.', true);
            const prefix = button.dataset.fieldLinkPrefix || '';
            if (prefix === '*:') {
                if (!value.includes(':') || value.toLowerCase().startsWith('minecraft:'))
                    return notice('Choose a custom item first.', true);
                value = value.slice(value.indexOf(':') + 1);
            } else if (prefix) {
                if (!value.startsWith(prefix)) return notice('Choose a linked record first.', true);
                value = value.slice(prefix.length);
            }
            let query = {};
            try { query = JSON.parse(button.dataset.fieldLinkQuery || '{}'); } catch (ignored) { }
            query[button.dataset.fieldLinkParameter] = value;
            const params = new URLSearchParams({module: button.dataset.fieldLinkModule, ...query});
            window.open(base + '?' + params.toString(), '_blank', 'noopener,noreferrer');
        });
        q('#workspace').querySelectorAll('[data-field-link-module]').forEach(button => {
            const input = button.closest('.webview-field')?.querySelector('input[name],select[name],textarea[name]');
            const update = () => {
                const value = input?.value?.trim() || '';
                const prefix = button.dataset.fieldLinkPrefix || '';
                button.hidden = prefix === '*:'
                    ? (!value.includes(':') || value.toLowerCase().startsWith('minecraft:'))
                    : prefix ? !value.startsWith(prefix) : !value;
            };
            input?.addEventListener('input', update);
            input?.addEventListener('change', update);
            update();
        });
        q('#workspace').querySelectorAll('[data-choice-search-input]').forEach(input => input.oninput = () => {
            const term = input.value.trim().toLowerCase();
            input.parentElement.querySelectorAll('[data-choice-search]').forEach(choice => {
                choice.hidden = !choice.dataset.choiceSearch.includes(term);
            });
        });
        q('#workspace').querySelectorAll('[data-choice-value]').forEach(input => input.onchange = () => {
            const amount = input.closest('.choice-row')?.querySelector('[data-choice-amount]');
            if (amount) amount.disabled = !input.checked;
            const group = input.closest('.webview-field')?.querySelector('[data-checkbox-group]');
            if (group?.dataset.stateBound === 'true') updateDraftField(group);
        });
        q('#workspace').querySelectorAll('[data-choice-amount]').forEach(input => {
            const update = () => {
                const group = input.closest('.webview-field')?.querySelector('[data-checkbox-group]');
                if (group?.dataset.stateBound === 'true') updateDraftField(group);
            };
            input.addEventListener('input', update);
            input.addEventListener('change', update);
        });
        q('#workspace').querySelectorAll('[data-multi-value]').forEach(input => input.onchange = () => {
            const group = input.closest('.webview-field')?.querySelector('[data-multiselect-group]');
            if (group?.dataset.stateBound === 'true') updateDraftField(group);
        });
        q('#workspace').querySelectorAll('[data-repeater]').forEach(button => button.onclick = () => {
            const path = button.dataset.path;
            const list = getPath(activeData?.state, path);
            if (!Array.isArray(list)) return;
            const index = Number(button.dataset.index);
            if (button.dataset.repeater === 'add') list.push(JSON.parse(button.dataset.default || '{}'));
            if (button.dataset.repeater === 'remove') list.splice(index, 1);
            if (button.dataset.repeater === 'up' && index > 0) [list[index - 1], list[index]] = [list[index], list[index - 1]];
            if (button.dataset.repeater === 'down' && index < list.length - 1) [list[index + 1], list[index]] = [list[index], list[index + 1]];
            viewDraftDirty = true;
            renderModule();
        });
        q('#workspace').querySelectorAll('[data-config-remove-key]').forEach(button => button.onclick = () => {
            const object = dataPathValue(activeData?.state, JSON.parse(button.dataset.configPath || '[]'));
            if (!object || typeof object !== 'object' || Array.isArray(object)) return;
            delete object[button.dataset.configRemoveKey];
            viewDraftDirty = true;
            renderModule();
        });
        q('#workspace').querySelectorAll('[data-config-remove-index]').forEach(button => button.onclick = () => {
            const list = dataPathValue(activeData?.state, JSON.parse(button.dataset.configPath || '[]'));
            if (!Array.isArray(list)) return;
            list.splice(Number(button.dataset.configRemoveIndex), 1);
            viewDraftDirty = true;
            renderModule();
        });
        q('#workspace').querySelectorAll('[data-config-add-key]').forEach(button => button.onclick = () => {
            const object = dataPathValue(activeData?.state, JSON.parse(button.dataset.configPath || '[]'));
            const controls = button.closest('.data-editor-add');
            const key = controls?.querySelector('[data-config-new-key]')?.value ?? '';
            if (!object || typeof object !== 'object' || Array.isArray(object)) return;
            if (!key.trim()) return notice('Enter a field name first.', true);
            if (Object.prototype.hasOwnProperty.call(object, key)) return notice('That field already exists.', true);
            const type = controls.querySelector('[data-config-add-type]')?.value || 'text';
            Object.defineProperty(object, key, {value: defaultDataValue(type), writable: true, enumerable: true, configurable: true});
            viewDraftDirty = true;
            renderModule();
        });
        q('#workspace').querySelectorAll('[data-config-add-index]').forEach(button => button.onclick = () => {
            const list = dataPathValue(activeData?.state, JSON.parse(button.dataset.configPath || '[]'));
            if (!Array.isArray(list)) return;
            const type = button.closest('.data-editor-add')?.querySelector('[data-config-add-type]')?.value || 'text';
            list.push(defaultDataValue(type));
            viewDraftDirty = true;
            renderModule();
        });
        q('#workspace').querySelectorAll('[data-list-search]').forEach(input => input.oninput = () => {
            const list = input.parentElement.querySelector('[data-search-list]');
            const term = input.value.trim().toLowerCase();
            list?.querySelectorAll('[data-search-text]').forEach(item => {
                item.hidden = !item.dataset.searchText.toLowerCase().includes(term);
            });
        });
        q('#workspace').querySelectorAll('[data-tree-toggle]').forEach(button => button.onclick = () => {
            const children = button.closest('[data-tree-node]')?.querySelector(':scope > [data-tree-children]');
            if (!children) return;
            children.hidden = !children.hidden;
            button.setAttribute('aria-expanded', String(!children.hidden));
        });
        q('#workspace').querySelectorAll('[data-tree-search]').forEach(input => input.oninput = () => {
            const term = input.value.trim().toLowerCase();
            const list = input.parentElement.querySelector('.tree-list');
            const filter = node => {
                const ownMatch = !term || node.dataset.searchText.includes(term);
                const children = node.querySelector(':scope > [data-tree-children]');
                const childNodes = children ? [...children.querySelectorAll(':scope > [data-tree-node]')] : [];
                const childMatches = childNodes.map(filter).some(Boolean);
                const matches = ownMatch || childMatches;
                node.hidden = !matches;
                if (children && term && childMatches) {
                    children.hidden = false;
                    node.querySelector(':scope > .tree-row [data-tree-toggle]')?.setAttribute('aria-expanded', 'true');
                } else if (children && !term) {
                    const expanded = children.dataset.defaultExpanded === 'true';
                    children.hidden = !expanded;
                    node.querySelector(':scope > .tree-row [data-tree-toggle]')?.setAttribute('aria-expanded', String(expanded));
                }
                return matches;
            };
            list?.querySelectorAll(':scope > [data-tree-node]').forEach(filter);
        });
        const workspace = q('#workspace');
        workspace.oninput = event => updateDraftField(event.target);
        workspace.onchange = event => {
            updateDraftField(event.target);
            if (event.target.matches('[data-config-key]')) renameDataKey(event.target);
        };
    }

    function updateDraftField(element) {
        if (element?.hasAttribute('data-config-value') && activeData?.state) {
            try {
                const path = JSON.parse(element.dataset.configPath || '[]');
                const kind = element.dataset.configKind;
                const value = kind === 'boolean' ? element.checked : kind === 'number'
                    ? (element.value === '' ? null : Number(element.value)) : element.value;
                setDataPath(activeData.state, path, value);
                viewDraftDirty = true;
            } catch (error) { notice(error.message, true); }
            return;
        }
        if (!element || !element.name || !element.closest('[data-view-form]')) return;
        if (!element.dataset.stateBound || !activeData?.state) {
            if (!['submit', 'button'].includes(element.type)) viewDraftDirty = true;
            return;
        }
        try {
            setPath(activeData.state, element.name, fieldInputValue(element));
            viewDraftDirty = true;
        } catch (error) { notice(error.message, true); }
    }

    function renameDataKey(input) {
        const parent = dataPathValue(activeData?.state, JSON.parse(input.dataset.configParent || '[]'));
        const previous = input.dataset.configOldKey || '';
        const next = input.value;
        if (!parent || typeof parent !== 'object' || Array.isArray(parent) || !Object.prototype.hasOwnProperty.call(parent, previous)) return;
        if (!next.trim()) { input.value = previous; return notice('Field names cannot be blank.', true); }
        if (next === previous) return;
        if (Object.prototype.hasOwnProperty.call(parent, next)) {
            input.value = previous;
            return notice('That field already exists.', true);
        }
        Object.defineProperty(parent, next, {value: parent[previous], writable: true, enumerable: true, configurable: true});
        delete parent[previous];
        viewDraftDirty = true;
        renderModule();
    }

    function connectConfigEvents() {
        if (!window.EventSource || configEvents) return;
        configEvents = new EventSource(base + '/api/events');
        configEvents.addEventListener('config.change', () => {
            if (viewDraftDirty) {
                draftConflictNotice('The configuration changed on the server. Your unsaved edits were preserved.');
                return;
            }
            if (activeId) openModule(activeId, activeQuery);
        });
    }

    async function boot() {
        try {
            modules = await request(base + '/api/modules');
            if (!modules.length) throw new Error('No editors are available for this link.');
            const route = new URLSearchParams(location.search);
            const requestedModule = route.get('module');
            const requestedQuery = Object.fromEntries([...route.entries()].filter(([key]) => key !== 'module'));
            activeId = modules.some(module => module.id === requestedModule) ? requestedModule : modules[0].id;
            renderNav();
            await openModule(activeId, activeId === requestedModule ? requestedQuery : {});
            connectConfigEvents();
        } catch (error) {
            q('#workspace').innerHTML = '<div class="empty">' + esc(error.message) + '</div>';
            setStatus('Could not start', true);
        }
    }

    boot();
})();
