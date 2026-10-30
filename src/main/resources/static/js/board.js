/* Kanban board: rendering, drag and drop, task dialog, comments, files, live updates. */
(() => {
  const { api, el, fmt, toast } = TF;
  const projectId = document.body.dataset.projectId;
  const meUsername = document.body.dataset.me;

  const COLUMNS = [
    { key: 'TODO', label: 'To do' },
    { key: 'IN_PROGRESS', label: 'In progress' },
    { key: 'REVIEW', label: 'In review' },
    { key: 'DONE', label: 'Done' }
  ];
  const PRIORITY = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', URGENT: 'Urgent' };

  let tasks = [];
  let members = [];
  let openTaskId = null;       // task currently shown in the dialog (null = creating)

  const boardEl = document.getElementById('board');
  const dialog = document.getElementById('taskDialog');
  const form = document.getElementById('taskForm');
  const side = document.getElementById('dlgSide');
  const formError = document.getElementById('formError');
  const mineOnly = document.getElementById('mineOnly');

  const me = () => members.find(m => m.username === meUsername);

  // ------------------------------------------------------------ loading
  async function load() {
    try {
      tasks = await api('GET', `/api/projects/${projectId}/tasks`);
      render();
    } catch (e) { toast(e.message, true); }
  }

  // ---------------------------------------------------------- rendering
  function dueChip(t) {
    if (!t.dueDate) return null;
    const hoursLeft = (new Date(t.dueDate) - Date.now()) / 36e5;
    const cls = t.overdue ? 'chip overdue' : (t.status !== 'DONE' && hoursLeft < 24 ? 'chip soon' : 'chip');
    return el('span', { class: cls }, (t.overdue ? 'Overdue ' : 'Due ') + fmt(t.dueDate));
  }

  function card(t) {
    const c = el('article', {
      class: `card prio-${t.priority.toLowerCase()}`, draggable: 'true', tabindex: '0',
      'data-id': t.id, 'aria-label': t.title,
      onclick: () => openTask(t.id),
      onkeydown: e => { if (e.key === 'Enter') openTask(t.id); },
      ondragstart: e => { e.dataTransfer.setData('text/plain', String(t.id)); e.dataTransfer.effectAllowed = 'move'; c.classList.add('dragging'); },
      ondragend: () => c.classList.remove('dragging')
    },
      el('h3', {}, t.title),
      el('div', { class: 'meta' },
        el('span', { class: 'chip' }, PRIORITY[t.priority]),
        dueChip(t),
        t.assigneeName ? el('span', {}, el('span', { class: 'avatar' }, t.assigneeName[0].toUpperCase()), t.assigneeName) : null,
        t.commentCount ? el('span', {}, `${t.commentCount} comment${t.commentCount > 1 ? 's' : ''}`) : null,
        t.attachmentCount ? el('span', {}, `${t.attachmentCount} file${t.attachmentCount > 1 ? 's' : ''}`) : null));
    return c;
  }

  function render() {
    const mine = mineOnly.checked && me();
    const visible = mine ? tasks.filter(t => t.assigneeId === me().id) : tasks;

    boardEl.replaceChildren(...COLUMNS.map(col => {
      const items = visible.filter(t => t.status === col.key);
      const body = el('div', { class: 'col-body', 'data-status': col.key },
        items.length ? items.map(card) : null);
      body.addEventListener('dragover', e => { e.preventDefault(); body.classList.add('over'); });
      body.addEventListener('dragleave', () => body.classList.remove('over'));
      body.addEventListener('drop', e => {
        e.preventDefault(); body.classList.remove('over');
        const id = Number(e.dataTransfer.getData('text/plain'));
        if (id) moveTask(id, col.key);
      });
      return el('section', { class: 'col', 'data-status': col.key, 'aria-label': col.label },
        el('div', { class: 'col-head' }, col.label, el('span', {}, items.length)), body);
    }));

    const done = tasks.filter(t => t.status === 'DONE').length;
    document.getElementById('progressText').textContent = `${done} of ${tasks.length} done`;
    document.getElementById('progressBar').style.width = tasks.length ? Math.round(done * 100 / tasks.length) + '%' : '0';
  }

  async function moveTask(id, status) {
    const t = tasks.find(x => x.id === id);
    if (!t || t.status === status) return;
    const previous = t.status;
    t.status = status; render();                     // optimistic
    try { await api('PATCH', `/api/tasks/${id}/status`, { status }); }
    catch (e) { t.status = previous; render(); toast(e.message, true); }
  }

  // ------------------------------------------------------------- dialog
  function fillAssignees(selectedId) {
    const sel = form.assigneeId;
    sel.replaceChildren(el('option', { value: '' }, 'Nobody yet'),
      ...members.map(m => el('option', { value: m.id }, m.fullName)));
    sel.value = selectedId ?? '';
  }

  function showError(msg) { formError.textContent = msg || ''; formError.hidden = !msg; }

  function openNew() {
    openTaskId = null;
    form.reset(); showError('');
    document.getElementById('dlgTitle').textContent = 'New task';
    document.getElementById('saveBtn').textContent = 'Create task';
    document.getElementById('deleteBtn').hidden = true;
    form.status.disabled = true; form.status.value = 'TODO';
    fillAssignees(null);
    side.hidden = true;
    dialog.showModal();
    form.title.focus();
  }

  async function openTask(id) {
    try {
      const d = await api('GET', `/api/tasks/${id}`);
      const t = d.task;
      openTaskId = id; showError('');
      document.getElementById('dlgTitle').textContent = 'Task details';
      document.getElementById('saveBtn').textContent = 'Save changes';
      document.getElementById('deleteBtn').hidden = false;
      form.status.disabled = false;
      form.title.value = t.title;
      form.description.value = t.description || '';
      form.status.value = t.status;
      form.priority.value = t.priority;
      form.dueDate.value = t.dueDate ? t.dueDate.slice(0, 16) : '';
      fillAssignees(t.assigneeId);
      renderSide(d);
      side.hidden = false;
      if (!dialog.open) dialog.showModal();
    } catch (e) { toast(e.message, true); }
  }

  function renderSide(d) {
    document.getElementById('fileList').replaceChildren(
      ...(d.attachments.length ? d.attachments.map(a =>
        el('li', {}, el('a', { href: `/api/attachments/${a.id}/download` }, a.fileName),
          el('small', {}, ` ${a.uploadedBy}, ${fmt(a.uploadedAt)}`)))
        : [el('li', {}, el('small', {}, 'No files yet.'))]));
    document.getElementById('commentList').replaceChildren(
      ...(d.comments.length ? d.comments.map(c =>
        el('li', {}, el('b', {}, c.author), ' ', el('small', {}, fmt(c.createdAt)), el('p', {}, c.text)))
        : [el('li', {}, el('small', {}, 'No comments yet. Start the discussion.'))]));
  }

  /** Refresh only comments and files, so someone's half-typed edit is not overwritten. */
  async function refreshSide() {
    if (!dialog.open || openTaskId === null) return;
    try { renderSide(await api('GET', `/api/tasks/${openTaskId}`)); }
    catch (_) { dialog.close(); toast('This task was deleted by someone else.'); }
  }

  form.addEventListener('submit', async e => {
    e.preventDefault(); showError('');
    const body = {
      title: form.title.value.trim(),
      description: form.description.value,
      priority: form.priority.value,
      dueDate: form.dueDate.value || null,
      assigneeId: form.assigneeId.value ? Number(form.assigneeId.value) : null
    };
    try {
      if (openTaskId === null) {
        await api('POST', `/api/projects/${projectId}/tasks`, body);
      } else {
        const before = tasks.find(t => t.id === openTaskId);
        await api('PUT', `/api/tasks/${openTaskId}`, body);
        if (before && before.status !== form.status.value) {
          await api('PATCH', `/api/tasks/${openTaskId}/status`, { status: form.status.value });
        }
      }
      dialog.close(); await load();
    } catch (err) { showError(err.message); }
  });

  document.getElementById('cancelBtn').addEventListener('click', () => dialog.close());
  dialog.addEventListener('close', () => { openTaskId = null; });
  document.getElementById('newTaskBtn').addEventListener('click', openNew);
  mineOnly.addEventListener('change', render);

  document.getElementById('deleteBtn').addEventListener('click', async () => {
    if (!confirm('Delete this task, its comments and files? This cannot be undone.')) return;
    try { await api('DELETE', `/api/tasks/${openTaskId}`); dialog.close(); await load(); }
    catch (err) { showError(err.message); }
  });

  document.getElementById('commentForm').addEventListener('submit', async e => {
    e.preventDefault();
    const box = e.target.text;
    if (!box.value.trim()) return;
    try { await api('POST', `/api/tasks/${openTaskId}/comments`, { text: box.value }); box.value = ''; await refreshSide(); load(); }
    catch (err) { toast(err.message, true); }
  });

  document.getElementById('fileInput').addEventListener('change', async e => {
    const file = e.target.files[0];
    if (!file) return;
    const data = new FormData(); data.append('file', file);
    try { await api('POST', `/api/tasks/${openTaskId}/attachments`, data); toast('File attached.'); await refreshSide(); load(); }
    catch (err) { toast(err.message, true); }
    e.target.value = '';
  });

  // --------------------------------------------------------- real time
  let timer;
  TF.onConnect(client => {
    load();                                         // catch up after (re)connecting
    client.subscribe(`/topic/projects/${projectId}`, msg => {
      const ev = JSON.parse(msg.body);
      clearTimeout(timer);
      timer = setTimeout(load, 150);                // several quick events become one refresh
      if (openTaskId !== null && Number(ev.taskId) === openTaskId) {
        if (ev.type === 'deleted') { dialog.close(); toast('This task was deleted by someone else.'); }
        else if (ev.type === 'commented' || ev.type === 'attached') refreshSide();
      }
    });
  });

  api('GET', `/api/projects/${projectId}/members`).then(m => { members = m; }).catch(() => {});
  load();
})();
