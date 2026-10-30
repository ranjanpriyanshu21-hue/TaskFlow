/* Shared helpers, API client, notifications and the real-time (STOMP/WebSocket) connection. */
const TF = (() => {
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

  async function api(method, url, body) {
    const opts = { method, headers: { [csrfHeader]: csrfToken } };
    if (body instanceof FormData) {
      opts.body = body;
    } else if (body !== undefined) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
    const res = await fetch(url, opts);
    if (res.redirected && res.url.includes('/login')) { location.href = '/login'; throw new Error('Session expired'); }
    if (!res.ok) {
      let msg = 'Something went wrong. Try again.';
      try {
        const j = await res.json();
        if (res.status === 400) msg = 'Check the form: a title is required.';
        else if (j.message) msg = j.message;
      } catch (_) { /* keep default */ }
      throw new Error(msg);
    }
    return res.status === 204 ? null : res.json();
  }

  /** Builds DOM nodes; text is always set as text, never as HTML. */
  function el(tag, props = {}, ...children) {
    const n = document.createElement(tag);
    for (const [k, v] of Object.entries(props)) {
      if (k === 'class') n.className = v;
      else if (k.startsWith('on')) n.addEventListener(k.slice(2), v);
      else if (v !== null && v !== undefined && v !== false) n.setAttribute(k, v === true ? '' : v);
    }
    for (const c of children.flat()) {
      if (c !== null && c !== undefined && c !== false) n.append(c.nodeType ? c : document.createTextNode(String(c)));
    }
    return n;
  }

  const fmt = iso => new Date(iso).toLocaleString([], { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });

  function toast(message, isError = false) {
    const box = document.getElementById('toasts');
    const t = el('div', { class: 'toast' + (isError ? ' err' : ''), role: isError ? 'alert' : 'status' }, message);
    box.append(t);
    setTimeout(() => t.remove(), 5000);
  }

  // ---------- notifications bell ----------
  let notifs = [];
  const bellBtn = document.getElementById('bellBtn');
  const bellMenu = document.getElementById('bellMenu');
  const bellList = document.getElementById('bellList');
  const bellCount = document.getElementById('bellCount');

  function renderBell() {
    const unseen = notifs.filter(n => !n.seen).length;
    bellCount.hidden = unseen === 0;
    bellCount.textContent = unseen > 9 ? '9+' : unseen;
    bellList.replaceChildren();
    if (!notifs.length) bellList.append(el('li', { class: 'none' }, 'Nothing yet. Assignments, comments and deadline reminders show up here.'));
    for (const n of notifs) {
      bellList.append(el('li', {
        class: n.seen ? '' : 'unseen',
        onclick: () => { if (n.projectId) location.href = '/projects/' + n.projectId; }
      }, n.message, el('small', {}, fmt(n.createdAt))));
    }
  }

  async function loadNotifications() {
    try { notifs = await api('GET', '/api/notifications'); renderBell(); } catch (_) { /* ignore */ }
  }

  bellBtn?.addEventListener('click', async () => {
    const opening = bellMenu.hidden;
    bellMenu.hidden = !opening;
    bellBtn.setAttribute('aria-expanded', String(opening));
    if (opening && notifs.some(n => !n.seen)) {
      try { await api('POST', '/api/notifications/read'); } catch (_) { /* ignore */ }
      bellCount.hidden = true;               // list keeps its highlight until reopened
    } else if (!opening) {
      notifs.forEach(n => (n.seen = true)); renderBell();
    }
  });
  document.addEventListener('click', e => {
    if (bellMenu && !bellMenu.hidden && !e.target.closest('.bell-wrap')) {
      bellMenu.hidden = true; bellBtn.setAttribute('aria-expanded', 'false');
      notifs.forEach(n => (n.seen = true)); renderBell();
    }
  });

  // ---------- real-time connection ----------
  const connectHandlers = [];
  const live = document.getElementById('liveStatus');
  const setLive = on => {
    if (!live) return;
    live.classList.toggle('on', on);
    live.querySelector('span').textContent = on ? 'Live' : 'Reconnecting';
  };

  if (bellBtn && window.StompJs) {
    const client = new StompJs.Client({
      brokerURL: (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws',
      reconnectDelay: 3000
    });
    client.onConnect = () => {
      setLive(true);
      client.subscribe('/user/queue/notifications', msg => {
        const n = JSON.parse(msg.body);
        notifs.unshift(n); renderBell(); toast(n.message);
      });
      connectHandlers.forEach(fn => fn(client));
    };
    client.onWebSocketClose = () => setLive(false);
    client.activate();
  }

  loadNotifications();
  return { api, el, fmt, toast, onConnect: fn => connectHandlers.push(fn) };
})();
