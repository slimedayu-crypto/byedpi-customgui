/* ByeByeDPI web UI — HTML/CSS presentation with a thin Android bridge. */
(function () {
  'use strict';

  const THEMES = [
    { id: 'system', name: 'Системная', bg: '#101114', accent: '#7c8cff', group: 'system' },
    { id: 'light', name: 'Светлая', bg: '#f4f4f6', accent: '#3f51b5', group: 'light' },
    { id: 'dark', name: 'Тёмная', bg: '#0d0d10', accent: '#8ab4f8', group: 'dark' },
    { id: 'minimal', name: 'Минимал', bg: '#f7f7f7', accent: '#111111', group: 'styles' },
    { id: 'terminal', name: 'Терминал', bg: '#03130a', accent: '#39ff88', group: 'styles' },
    { id: 'bento', name: 'Бенто', bg: '#101014', accent: '#ff8fb1', group: 'styles' },
    { id: 'aurora', name: 'Аврора', bg: '#0a0b18', accent: '#9d7cff', group: 'styles' },
    { id: 'brutal', name: 'Брутал', bg: '#f2f2f2', accent: '#000000', group: 'styles' },
    { id: 'soft', name: 'Мягкий', bg: '#141826', accent: '#8fb8ff', group: 'styles' },
    { id: 'gauge', name: 'Прибор', bg: '#2a1c10', accent: '#e8b76a', group: 'styles' },
    { id: 'dotmatrix', name: 'Dot-matrix', bg: '#050505', accent: '#ff2f2f', group: 'styles' },
    { id: 'flow', name: 'Течение', bg: '#062a2a', accent: '#7fffe0', group: 'styles' },
    { id: 'orbit', name: 'Орбита', bg: '#0e1029', accent: '#d4ff3f', group: 'styles' },
    { id: 'stamp', name: 'Штамп', bg: '#f3edda', accent: '#b3261e', group: 'styles' },
    { id: 'metro', name: 'Метро', bg: '#f6f6f6', accent: '#e2231a', group: 'styles' },
    { id: 'tuner', name: 'Тюнер', bg: '#241708', accent: '#f0c070', group: 'styles' },
    { id: 'bricks', name: 'Кирпичики', bg: '#fdf3d8', accent: '#e63946', group: 'styles' },
    { id: 'pixel', name: 'Пиксель', bg: '#0b1026', accent: '#33e06a', group: 'styles' },
    { id: 'doge', name: 'Вау-обход', bg: '#ffd93b', accent: '#e8332a', group: 'styles' },
    { id: 'win98', name: 'Ошибка 98', bg: '#008080', accent: '#000080', group: 'styles' },
    { id: 'impact', name: 'Impact', bg: '#0b0b0c', accent: '#ff3b30', group: 'styles' },
    { id: 'sigma', name: 'Сигма', bg: '#0e0d0a', accent: '#e6c268', group: 'styles' }
  ];

  // Each style keeps its own voice: brand, status copy, tab names and screen titles.
  const META = {
    minimal: { brand: 'MINIMAL', off: 'Выключено', on: 'Включено', tabs: ['ГЛАВНАЯ', 'СТРАТЕГИИ', 'ПРИЛОЖЕНИЯ', 'НАСТРОЙКИ'] },
    terminal: { brand: 'TERMINAL', off: 'НЕТ СИГНАЛА', on: 'СИГНАЛ ЕСТЬ', subOff: '> нажмите для запуска', subOn: '> туннель активен', tabs: ['УЗЕЛ', 'ПРОФИЛИ', 'МОДУЛИ', 'КОНФИГ'], strategies: 'ПРОФИЛИ', strategiesSub: 'Выберите профиль обхода.', apps: 'МОДУЛИ', settings: 'КОНФИГ' },
    bento: { brand: 'BENTO', off: 'Не подключено', on: 'Подключено', tabs: ['ГЛАВНАЯ', 'СТРАТЕГИИ', 'ПРИЛОЖЕНИЯ', 'НАСТРОЙКИ'] },
    aurora: { brand: 'AURORA', off: 'Поток погас', on: 'Сияние активно', tabs: ['ГЛАВНАЯ', 'СТРАТЕГИИ', 'ПРИЛОЖЕНИЯ', 'НАСТРОЙКИ'] },
    brutal: { brand: 'БРУТАЛ', off: 'ОБХОД ВЫКЛ', on: 'ОБХОД ВКЛ', tabs: ['ГЛАВНАЯ', 'СТРАТЕГИИ', 'ПРИЛОЖЕНИЯ', 'НАСТРОЙКИ'] },
    soft: { brand: 'SOFT', off: 'Тихо', on: 'Спокойно', tabs: ['ГЛАВНАЯ', 'СТРАТЕГИИ', 'ПРИЛОЖЕНИЯ', 'НАСТРОЙКИ'] },
    gauge: { brand: 'ByeByeDPI', off: 'Эфир заглушён', on: 'Станция найдена', subOff: 'Покрутите ручку, чтобы настроиться', subOn: 'Сигнал стабилен', tabs: ['ЭФИР', 'ВОЛНЫ', 'ЧАСТОТЫ', 'НАСТРОЙКИ'], strategies: 'Волны', apps: 'Частоты' },
    dotmatrix: { brand: 'BYEBYEDPI', off: 'ОТКЛЮЧЕНО', on: 'ПОДКЛЮЧЕНО', subOff: 'VPN РЕЖИМ', tabs: ['ГЛАВНАЯ', 'СТРАТЕГИИ', 'ФИЛЬТР', 'СИСТЕМА'], strategies: 'СТРАТЕГИИ', apps: 'ФИЛЬТР', settings: 'СИСТЕМА' },
    flow: { brand: 'BYEBYEDPI', off: 'Поток остановлен', on: 'Поток идёт', subOff: 'Блокировка держит соединение', tabs: ['ГЛАВНАЯ', 'ТЕЧЕНИЯ', 'ПРИЛОЖЕНИЯ', 'НАСТРОЙКИ'], strategies: 'Течения', strategiesSub: 'Выберите течение обхода.' },
    orbit: { brand: 'BYEBYEDPI', off: 'Орбита пуста', on: 'Орбита активна', subOff: 'Нажмите ядро, чтобы запустить обход', subOn: 'Спутники на орбите', tabs: ['ГЛАВНАЯ', 'МАНЕВРЫ', 'СПУТНИКИ', 'НАСТРОЙКИ'], strategies: 'Маневры', apps: 'Спутники' },
    stamp: { brand: 'ByeByeDPI', off: 'Пропуск не оформлен', on: 'Доступ разрешён', subOff: 'Поставьте печать, чтобы открыть доступ', tabs: ['ГЛАВНАЯ', 'СТРАТЕГИИ', 'ПРОПУСКА', 'ПЕЧАТЬ'], strategies: 'Стратегии', apps: 'Пропуска', settings: 'Печать' },
    metro: { brand: 'ByeByeDPI', off: 'Линия закрыта', on: 'Линия открыта', subOff: 'Движение приостановлено', subOn: 'Составы идут', tabs: ['ГЛАВНАЯ', 'ЛИНИИ', 'СТАНЦИИ', 'НАСТРОЙКИ'], strategies: 'Линии', apps: 'Станции' },
    tuner: { brand: 'ByeByeDPI', off: 'Эфир заглушён', on: 'Станция найдена', tabs: ['ЭФИР', 'ВОЛНЫ', 'ПРИЛОЖЕНИЯ', 'НАСТРОЙКИ'], strategies: 'Волны', strategiesSub: 'Настройте волну обхода.' },
    bricks: { brand: 'ByeByeDPI', off: 'Стена на месте', on: 'Стена пробита', subOff: 'Блокировка держится', subOn: 'Проход открыт', tabs: ['ГЛАВНАЯ', 'КЛАДКА', 'ПРИЛОЖЕНИЯ', 'НАСТРОЙКИ'], strategies: 'Кладка' },
    pixel: { brand: 'BYEBYEDPI', off: 'WALL AHEAD', on: 'LEVEL CLEAR!', subOff: 'PRESS START TO BREAK THROUGH', subOn: 'POWER ON', tabs: ['START', 'RUN', 'GEAR', 'MENU'], strategies: 'RUN', strategiesSub: 'Выберите забег.', apps: 'GEAR', settings: 'MENU', tune: 'HI-SCORE' },
    doge: { brand: 'ByeByeDPI', off: 'Не подключено', on: 'Подключено!!', subOff: 'нажми. такой старт.', subOn: 'такой обход. очень хорошо', tabs: ['ГЛАВНАЯ', 'СТРАТЕГИИ', 'ФИЛЬТР', 'НАСТРОЙКИ'], strategies: 'Выбор', strategiesSub: 'такой выбор стратегий', apps: 'Фильтр' },
    win98: { brand: 'ByeByeDPI.exe', off: 'Обнаружена блокировка', on: 'Блокировка успешно обойдена!', subOff: 'Сайты недоступны. Хотите попытаться обойти?', subOn: 'Можно открывать сайты.', tabs: ['ГЛАВНАЯ', 'СТРАТЕГИИ', 'ПРИЛОЖЕНИЯ', 'НАСТРОЙКИ'], strategies: 'Свойства соединения', strategiesSub: 'Выберите стратегию обхода.', apps: 'Фильтр приложений', tune: 'Подбор стратегий' },
    impact: { brand: 'BYEBYEDPI', off: 'ДОСТУП ЗАКРЫТ', on: 'ДОСТУП ОТКРЫТ', subOff: 'VPN · сеть не обработана', tabs: ['ДОСТУП', 'СТРАТЕГИИ', 'ПРИЛОЖЕНИЯ', 'НАСТРОЙКИ'] },
    sigma: { brand: 'BYEBYEDPI', off: 'ОБЫЧНЫЙ ЧЕЛОВЕК', on: 'СИГМА', subOff: '«Блокировки — это судьба». Так думают слабые.', subOn: 'Блокировка обойдена. Без лишних слов.', tabs: ['ГЛАВНАЯ', 'РЕЙТИНГ', 'ПРИЛОЖЕНИЯ', 'НАСТРОЙКИ'], strategies: 'РЕЙТИНГ СТРАТЕГИЙ', strategiesSub: 'Лучший результат — это шум для остальных.' }
  };

  const SIGILS = {
    dotmatrix: '▚', flow: '≈', orbit: '◎', metro: '◉', stamp: '✓', win98: '🖥',
    pixel: '★', doge: '🐶', sigma: 'Σ', gauge: '◔', tuner: '◈', bricks: '▦',
    terminal: '&gt;_', minimal: '·', impact: '!', brutal: '▮', bento: '▣',
    aurora: '✦', soft: '○'
  };

  function themeMeta() {
    return META[state && state.theme] || {};
  }

  function applyThemeMeta() {
    const m = themeMeta();
    if ($('brand')) $('brand').textContent = m.brand || 'BYEBYEDPI';
    const tabs = m.tabs;
    if (tabs) document.querySelectorAll('#tabbar .tab-label').forEach((el, i) => {
      if (tabs[i]) el.textContent = tabs[i];
    });
    const set = (id, text) => { if (text && $(id)) $(id).textContent = text; };
    set('strategiesTitle', m.strategies);
    set('strategiesSub', m.strategiesSub);
    set('appsTitle', m.apps);
    set('settingsTitle', m.settings);
    set('tuneTitle', m.tune);
    set('editorTitle', m.editor);
    if ($('sigil')) $('sigil').innerHTML = SIGILS[state && state.theme] || '';
    document.body.dataset.voice = state ? state.theme : '';
  }

  const DNS = [
    { id: '1.1.1.1', label: 'Cloudflare' },
    { id: '8.8.8.8', label: 'Google' },
    { id: '77.88.8.8', label: 'Яндекс' },
    { id: '', label: 'Системный' }
  ];

  let state = null;
  let tab = 'home';
  let pendingTheme = null;
  let themeGroup = 'styles';
  let selectedApps = new Set();
  let appsQuery = '';
  let tuneState = null;

  const $ = (id) => document.getElementById(id);
  const esc = (v) => String(v == null ? '' : v)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  const bridge = () => (window.Native && window.Native.state ? window.Native : null);
  const call = (name, ...args) => {
    const n = bridge();
    if (!n || !n[name]) return null;
    try { return n[name](...args); } catch (e) { console.error(e); return null; }
  };
  const bool = (v) => v === true || v === 'true';

  function demoState() {
    return {
      theme: 'sigma', tab: tab, running: false, mode: 'vpn', runMode: 'vpn',
      proxy: '127.0.0.1:1080', strategyCmd: '-q2 -s2 -s3+s -r3 -s4 -r4 -s5+s',
      strategyName: 'Сигма', cmdEnabled: true, animations: true, dnsIp: '1.1.1.1',
      ipv6: false, autoStrategy: false, autoConnect: false, autoStart: false,
      filterMode: 'blacklist', selectedCount: 2, version: 'debug',
      strategies: [
        { name: 'Сигма', cmd: '-q2 -s2 -s3+s -r3 -s4 -r4 -s5+s' },
        { name: 'Бета', cmd: '-d1 -s3+s -a1' },
        { name: 'Омега', cmd: '-o1 -a1 -r-5+se' },
        { name: 'NPC', cmd: '-d1 -s1+s -d3+s -s6+s' }
      ],
      apps: [
        { label: 'TikTok', pkg: 'com.zhiliaoapp.musically', selected: true },
        { label: 'Telegram', pkg: 'org.telegram.messenger', selected: true },
        { label: 'YouTube', pkg: 'com.google.android.youtube', selected: false },
        { label: '2ГИС', pkg: 'ru.dublgis.dgismobile', selected: false },
        { label: 'Anixart', pkg: 'com.swiftsoft.anixartd', selected: false }
      ]
    };
  }

  let appsCache = null;

  function loadApps() {
    if (appsCache) return appsCache;
    try {
      const raw = call('apps');
      if (raw) appsCache = JSON.parse(raw);
    } catch (e) { console.error(e); }
    return appsCache || [];
  }

  function applyState(raw) {
    try { state = typeof raw === 'string' ? JSON.parse(raw) : raw; } catch (e) { console.error(e); return; }
    if (!state || !state.theme) return;
    if (!state.apps || !state.apps.length) state.apps = loadApps();
    else appsCache = state.apps;
    selectedApps = new Set((state.apps || []).filter(a => a.selected).map(a => a.pkg));
    document.body.className = 'theme-' + state.theme + (bool(state.animations) ? '' : ' no-anim');
    render();
  }

  function render() {
    if (!state) return;
    applyThemeMeta();
    renderNav();
    // Only the visible screen is rebuilt: hidden screens are refreshed when the
    // user opens them, which keeps taps and preference changes responsive.
    if (tab === 'home') renderHome();
    else if (tab === 'strategies' || tab === 'tune') renderStrategies();
    else if (tab === 'apps') renderApps();
    else if (tab === 'editor') renderEditor();
    else if (tab === 'settings') renderSettings();
    renderInlineTune();
  }

  function renderNav() {
    const activeTab = tab === 'tune' ? 'strategies' : tab;
    document.querySelectorAll('#tabbar .tab').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.tab === activeTab);
    });
    document.querySelectorAll('.screen').forEach(s => {
      s.classList.toggle('active', s.id === 'screen-' + tab);
    });
  }

  function renderHome() {
    const running = bool(state.running);
    const m = themeMeta();
    $('homeEyebrow').textContent = (state.mode === 'vpn' ? 'VPN' : 'PROXY') +
      (running ? ' · ' + (state.runMode === 'proxy' ? 'PROXY' : 'VPN') : ' · НЕ ПОДКЛЮЧЕНО');
    $('homeTitle').textContent = running ? (m.on || 'Подключено') : (m.off || 'Отключено');
    $('homeSub').textContent = running
      ? (m.subOn || 'Обход активен · ' + state.proxy)
      : (m.subOff || 'Нажмите, чтобы подключиться');
    $('powerBtn').classList.toggle('on', running);
    $('homeStrategyName').textContent = state.strategyName || 'Не выбрана';
    $('homeStrategyCmd').textContent = state.strategyCmd || '—';
    $('chipProxy').textContent = state.proxy;
    $('chipNetwork').textContent = state.network || 'Сеть';
    const n = Number(state.selectedCount || 0);
    const bySelected = state.filterMode === 'whitelist';
    const byExcluded = state.filterMode === 'blacklist';
    $('chipApps').textContent = byExcluded
      ? (n === 0 ? 'Все' : 'Кроме ' + n)
      : bySelected
        ? (n === 0 ? 'Не выбраны' : 'Выбрано ' + n)
        : 'Все';
    $('chipStrategy').textContent = state.strategyName || (state.strategyCmd ? 'Своя' : '—');

    const best = state.best || null;
    const bestCard = $('homeBest');
    if (best && best.cmd) {
      bestCard.hidden = false;
      bestCard.dataset.cmd = best.cmd;
      bestCard.classList.toggle('selected', best.cmd === state.strategyCmd);
      $('homeBestPct').textContent = (best.percent || 0) + '% · ' + (best.score || 0) + '/' + (best.total || 0);
      $('homeBestBar').style.width = Math.min(100, best.percent || 0) + '%';
      $('homeBestCmd').textContent = best.cmd;
    } else {
      bestCard.hidden = true;
    }
  }

  // Commands arrive from several sources (pinned history, the asset list and
  // the live test state), so compare them whitespace-insensitively.
  const normCmd = (c) => String(c || '').trim().replace(/\s+/g, ' ');

  function renderStrategies() {
    const list = $('strategyList');
    const live = {};
    ((tuneState && tuneState.strategies) || []).forEach(x => {
      live[normCmd(x.cmd)] = x;
    });

    const items = (state.strategies || []).map(s => {
      const l = live[normCmd(s.cmd)];
      if (!l || (!l.total && !l.done)) return s;
      const done = l.done || (l.completed ? l.total : 0);
      return Object.assign({}, s, {
        score: l.success || 0,
        total: l.total || 0,
        done,
        percent: done > 0 ? Math.round((l.success || 0) * 100 / done) : 0,
        tested: done > 0 || !!l.total,
        running: !!l.total && !l.completed
      });
    });

    // A running test can know about a command that is not in the saved list
    // yet (for example a freshly merged strategy). Show it right away.
    const known = new Set(items.map(i => normCmd(i.cmd)));
    Object.keys(live).forEach(key => {
      const l = live[key];
      if (known.has(key) || !l.total) return;
      const done = l.done || (l.completed ? l.total : 0);
      items.push({
        name: '', cmd: l.cmd, score: l.success || 0, total: l.total, done,
        percent: done > 0 ? Math.round((l.success || 0) * 100 / done) : 0,
        tested: true, running: !!l.total && !l.completed
      });
    });

    items.sort((a, b) => {
      if (a.cmd === state.strategyCmd) return -1;
      if (b.cmd === state.strategyCmd) return 1;
      return (b.percent || 0) - (a.percent || 0) || (b.score || 0) - (a.score || 0);
    });

    const best = (state.best && state.best.total > 0) ? state.best : null;
    if (best) {
      $('bestScore').innerHTML = `${best.score}<i>/${best.total}</i>`;
      $('bestSub').textContent = `${best.percent}% доменов`;
    } else {
      $('bestScore').innerHTML = '—<i>/—</i>';
      $('bestSub').textContent = 'проверка ещё не запускалась';
    }

    list.innerHTML = items.length ? items.map((s) => {
      const active = s.cmd === state.strategyCmd;
      const tested = !!s.tested;
      const done = Number(s.done || 0);
      const denom = done || Number(s.total || 0);
      const pct = denom > 0 ? Math.round(Number(s.score || 0) * 100 / denom) : 0;
      const running = !!s.running && !done;
      const tone = !tested ? 'none' : pct >= 90 ? 'good' : pct >= 50 ? 'mid' : 'bad';
      return `
      <button class="strategy-row ${active ? 'active' : ''} ${s.running ? 'running' : ''}"
              data-action="apply-row" data-cmd="${esc(s.cmd)}">
        <span class="strategy-cmd mono">${esc(s.cmd)}</span>
        <span class="strategy-metric">
          <span class="strategy-frac ${tone}">${tested ? (running ? '0/0' : Number(s.score || 0) + '/' + denom) : '—'}</span>
          <span class="strategy-score ${tone}">${tested && denom > 0 ? pct + '%' : '—'}</span>
        </span>
      </button>`;
    }).join('') : `
      <div class="empty">
        <p>Список стратегий пуст.</p>
        <p class="muted">Запустите проверку — найденные стратегии появятся здесь.</p>
      </div>`;
  }

  function renderApps() {
    const mode = state.filterMode === 'whitelist' ? 'whitelist'
      : state.filterMode === 'blacklist' ? 'blacklist' : 'disable';
    document.querySelectorAll('#appsMode button').forEach(b => {
      b.classList.toggle('active', b.dataset.mode === mode);
    });
    $('appsHint').textContent = mode === 'whitelist'
      ? 'Обрабатываются только выбранные приложения'
      : mode === 'blacklist'
        ? 'Выбранные приложения идут напрямую, остальные обрабатываются'
        : 'Обрабатываются все приложения';
    const list = $('appsList');
    const scroll = list.scrollTop;
    const q = appsQuery.trim().toLowerCase();
    const apps = (state.apps || []).filter(a =>
      !q || (a.label || '').toLowerCase().includes(q) || (a.pkg || '').toLowerCase().includes(q));
    list.innerHTML = apps.map(a => `
      <button class="row-card app ${selectedApps.has(a.pkg) ? 'selected' : ''}"
              data-action="app" data-pkg="${esc(a.pkg)}">
        <img class="app-icon" alt="" src="${a.icon || PIXEL}" ${a.icon ? '' : `data-icon="${esc(a.pkg)}"`}>
        <span class="grow">
          <span class="title">${esc(a.label)}</span>
          <span class="sub mono">${esc(a.pkg)}</span>
        </span>
        <span class="check">${selectedApps.has(a.pkg) ? '✓' : ''}</span>
      </button>`).join('') || '<div class="empty"><p>Ничего не найдено</p></div>';
    list.scrollTop = scroll;
    fillIcons();
    document.querySelectorAll('[data-quick]').forEach(chip => {
      const pkgs = QUICK_APPS[chip.dataset.quick] || [];
      chip.classList.toggle('selected', pkgs.some(p => selectedApps.has(p)));
    });
  }

  // Icons arrive on demand from the Android side. Asking for only the rows that
  // are actually visible keeps the first paint and scrolling smooth.
  const PIXEL = 'data:image/gif;base64,R0lGODlhAQABAAAAACw=';
  let iconBudget = 0;
  function fillIcons() {
    const pending = document.querySelectorAll('#appsList img[data-icon]');
    const slice = Array.prototype.slice.call(pending, 0, 48);
    slice.forEach(img => {
      const pkg = img.getAttribute('data-icon');
      img.removeAttribute('data-icon');
      const src = call('icon', pkg);
      if (src) img.src = src;
    });
    iconBudget = pending.length - slice.length;
  }

  function renderTune() {
    const t = tuneState;
    const running = !!(t && t.running);
    const strategies = (t && t.strategies) || [];
    const best = t && t.bestTotal ? t.bestScore : null;

    $('tuneBest').textContent = best == null ? '—' : best;
    $('tuneTotal').textContent = t && t.bestTotal ? '/' + t.bestTotal : '/32';
    const pct = t && t.bestTotal ? Math.round(t.bestScore * 100 / t.bestTotal) : 0;
    $('tuneBar').style.width = pct + '%';
    $('tuneBestCmd').textContent = t && t.bestCommand ? t.bestCommand : 'Проверка ещё не запускалась';
    $('tuneHint').textContent = running
      ? 'Проверка ' + Math.max(1, t.current) + ' из ' + t.total + ' · не закрывайте приложение'
      : t && t.failed ? 'Не удалось запустить проверку. Попробуйте ещё раз.'
      : t && t.interrupted ? 'Прошлая проверка была прервана'
      : strategies.length ? 'Проверено стратегий: ' + strategies.filter(s => s.completed).length
      : 'Нажмите «Начать проверку» — это займёт несколько минут';
    $('tuneStart').disabled = running;
    $('tuneStop').disabled = !running;
    $('tuneStop').textContent = t && t.stopping ? 'Останавливаю…' : 'Остановить';

    const list = $('tuneList');
    list.innerHTML = strategies.length ? strategies.map((s, i) => `
      <div class="row-card tune-row ${s.cmd === (t.bestCommand || '') ? 'selected' : ''}">
        <span class="idx">${String(i + 1).padStart(2, '0')}</span>
        <span class="grow">
          <span class="title mono">${esc(s.cmd)}</span>
        </span>
        ${s.completed
          ? `<button class="score apply-btn" data-action="apply-strategy" data-cmd="${esc(s.cmd)}">
               ${s.success}<small>/${s.total}</small><em>Применить</em>
             </button>`
          : `<span class="score">${running && i + 1 === t.current ? '…' : '—'}</span>`}
      </div>`).join('') : `
      <div class="empty">
        <p>Пока нет результатов.</p>
        <p class="muted">Лучшая стратегия сохранится и её можно будет применить.</p>
      </div>`;
  }

  function renderInlineTune() {
    const t = tuneState;
    if (!t) return;
    const list = t.strategies || [];
    const running = !!t.running;
    const checked = list.filter(s => (s.done || 0) > 0).length;

    $('inlineStart').disabled = running;
    $('inlineStop').disabled = !running;
    $('inlineStop').textContent = t.stopping ? 'Стоп…' : 'Стоп';

    // During a run show the best partial result (3/7, 8/12, …) so numbers keep
    // moving without waiting for the full check to finish.
    const live = list.filter(s => (s.done || (s.completed ? s.total : 0)) > 0)
      .map(s => {
        const done = s.done || s.total || 0;
        return { score: s.success || 0, done, pct: done ? (s.success || 0) * 100 / done : 0 };
      })
      .sort((a, b) => b.pct - a.pct || b.score - a.score)[0];

    if (running && live) {
      $('bestScore').innerHTML = `${live.score}<i>/${live.done}</i>`;
      $('bestSub').textContent = Math.round(live.pct) + '% доменов · идёт проверка';
    } else if (t.bestTotal) {
      $('bestScore').innerHTML = `${t.bestScore}<i>/${t.bestTotal}</i>`;
      $('bestSub').textContent = Math.round(t.bestScore * 100 / t.bestTotal) + '% доменов';
    } else if (running) {
      $('bestSub').textContent = 'идёт проверка · проверено стратегий: ' + checked;
    }
  }

  function renderEditor() {
    if (!$('cmdInput')) return;
    if (document.activeElement !== $('cmdInput')) $('cmdInput').value = state.command || '';
    $('cmdPresets').innerHTML = (state.strategies || []).map(s => `
      <button class="chip" data-action="preset" data-cmd="${esc(s.cmd)}">${esc(s.name || s.cmd)}</button>`).join('');
  }

  function renderSettings() {
    const anims = bool(state.animations);
    $('themeTitle').textContent = (THEMES.find(t => t.id === state.theme) || THEMES[0]).name;
    $('themeSub').textContent = 'Тема · анимации ' + (anims ? 'включены' : 'выключены');
    const t = THEMES.find(x => x.id === state.theme) || THEMES[0];
    $('themeDot').style.background = `radial-gradient(circle at 35% 35%, ${t.accent}, ${t.bg} 72%)`;

    const mode = state.mode === 'proxy' ? 'proxy' : 'vpn';
    const dnsIp = state.dnsIp == null ? '1.1.1.1' : state.dnsIp;
    const dnsChips = DNS.map(d => `
      <button class="chip ${dnsIp === d.id ? 'active' : ''}" data-action="dns" data-ip="${esc(d.id)}">
        ${esc(d.label)}</button>`).join('') +
      `<button class="chip" data-action="dns-custom">Свой…</button>`;

    const list = $('settingsList');
    const scroll = $('main').scrollTop;
    list.innerHTML = `
      <div class="cat">ПОДКЛЮЧЕНИЕ</div>
      <div class="set-block">
        <div class="set-label">Режим</div>
        <div class="segmented" id="modeSeg">
          <button data-action="mode" data-mode="vpn" class="${mode === 'vpn' ? 'active' : ''}">VPN</button>
          <button data-action="mode" data-mode="proxy" class="${mode === 'proxy' ? 'active' : ''}">Proxy</button>
        </div>
      </div>
      <div class="set-block">
        <div class="set-label">DNS</div>
        <div class="chips">${dnsChips}</div>
      </div>
      ${rowSwitch('ipv6', 'IPv6', 'Использовать IPv6-адреса', bool(state.ipv6))}
      <div class="cat">АВТОМАТИЗАЦИЯ</div>
      ${rowSwitch('auto_strategy_enable', 'Автоподбор стратегии', 'Проверять стратегии автоматически', bool(state.autoStrategy))}
      ${rowSwitch('auto_connect', 'Автоподключение', 'При открытии приложения', bool(state.autoConnect))}
      ${rowSwitch('autostart', 'Автозапуск', 'При включении устройства', bool(state.autoStart))}
      <div class="cat">BYEDPI</div>
      ${rowLink('appearance', 'Внешний вид', 'Тема · ' + ((THEMES.find(t => t.id === state.theme) || THEMES[0]).name))}
      ${rowLink('editor', 'Редактор', state.cmdEnabled ? 'Своя команда ByeDPI' : 'Командная строка выключена')}
      ${rowLink('test', 'Подбор стратегий', 'Проверка доменов и стратегий')}
      ${rowLink('domains', 'Списки доменов', 'Пользовательские списки')}
      <div class="cat">ОФОРМЛЕНИЕ</div>
      ${rowSwitch('theme_animations', 'Анимации темы', 'Появление, нажатия, кнопка питания', anims)}
      <div class="cat">О ПРИЛОЖЕНИИ</div>
      ${rowLink('license', 'Лицензия', 'GNU General Public License v3.0')}
      <div class="set-row static">
        <span class="grow"><span class="title">Версия</span>
        <span class="sub">ByeDPI Android ${esc(state.version || '')}</span></span>
      </div>`;
    window.requestAnimationFrame(() => { $('main').scrollTop = scroll; });
    applySettingsSearch();
  }

  function rowSwitch(key, title, sub, checked) {
    return `<div class="set-row">
      <span class="grow"><span class="title">${esc(title)}</span><span class="sub">${esc(sub)}</span></span>
      <button class="switch ${checked ? 'on' : ''}" role="switch" aria-checked="${checked}"
              data-action="switch" data-key="${esc(key)}" data-value="${checked}"></button>
    </div>`;
  }

  function rowLink(action, title, sub) {
    return `<button class="set-row" data-action="${action === 'license' ? 'license' : 'open'}"
              ${action === 'license' ? '' : `data-screen="${esc(action)}"`}>
      <span class="grow"><span class="title">${esc(title)}</span><span class="sub">${esc(sub)}</span></span>
      <span class="chev">›</span>
    </button>`;
  }

  function applySettingsSearch() {
    const q = ($('settingsSearch').value || '').trim().toLowerCase();
    const list = $('settingsList');
    let visibleInSection = false;
    const nodes = Array.from(list.children);
    nodes.forEach(node => {
      if (node.classList.contains('cat')) {
        node.dataset.pending = '1';
        node.style.display = q ? 'none' : '';
        visibleInSection = false;
        return;
      }
      const match = !q || node.textContent.toLowerCase().includes(q);
      node.style.display = match ? '' : 'none';
      if (match) {
        visibleInSection = true;
        if (q) {
          let prev = node.previousElementSibling;
          while (prev) {
            if (prev.classList.contains('cat')) { prev.style.display = ''; break; }
            prev = prev.previousElementSibling;
          }
        }
      }
    });
    if (q) {
      nodes.forEach(node => {
        if (!node.classList.contains('cat')) return;
        let next = node.nextElementSibling;
        let has = false;
        while (next && !next.classList.contains('cat')) {
          if (next.style.display !== 'none') has = true;
          next = next.nextElementSibling;
        }
        node.style.display = has ? '' : 'none';
      });
    }
  }

  function hashColor(seed) {
    let h = 0;
    for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) % 360;
    return `hsl(${h}, 62%, 58%)`;
  }

  function switchTab(next) {
    if (!next) return;
    tab = next;
    if (state) state.tab = next;
    if (next !== 'tune') call('setTab', next);
    // Pull a fresh snapshot when opening data-heavy screens: test results are
    // produced by a service and the page has no other way to notice changes.
    if (next === 'strategies' || next === 'tune' || next === 'home') refresh();
    else render();
    if (next === 'tune') renderTune();
    $('main').scrollTop = 0;
  }

  function refresh() {
    const raw = call('state');
    if (raw) applyState(raw);
  }

  /* ---------- sheets ---------- */

  function openSheet(html, cls) {
    const sheet = $('sheet');
    sheet.hidden = false;
    sheet.className = 'sheet open ' + (cls || '');
    sheet.innerHTML = html;
    $('overlay').hidden = false;
    requestAnimationFrame(() => $('overlay').classList.add('show'));
  }

  function closeSheet() {
    $('sheet').classList.remove('open');
    $('overlay').classList.remove('show');
    setTimeout(() => { $('overlay').hidden = true; $('sheet').hidden = true; }, 180);
  }

  function openThemeSheet() {
    pendingTheme = state.theme;
    themeGroup = (THEMES.find(t => t.id === state.theme) || {}).group || 'styles';
    renderThemeSheet();
  }

  function renderThemeSheet() {
    const groups = [['styles', 'Стили'], ['system', 'Системная'], ['light', 'Светлая'], ['dark', 'Тёмная']];
    const visible = THEMES.filter(t => t.group === themeGroup);
    openSheet(`
      <div class="sheet-head">
        <h3>Внешний вид</h3>
        <button class="icon-btn" data-action="close-sheet">✕</button>
      </div>
      <div class="segmented small" id="themeTabs">
        ${groups.map(([g, label]) => `<button data-group="${g}" class="${g === themeGroup ? 'active' : ''}">${label}</button>`).join('')}
      </div>
      <div class="theme-grid">
        ${visible.map(t => `
          <button class="theme-item ${t.id === pendingTheme ? 'selected' : ''}" data-action="pick-theme" data-theme="${t.id}">
            <span class="preview" style="--pbg:${t.bg};--pac:${t.accent}">
              <span class="preview-dot"></span>
            </span>
            <span class="name">${esc(t.name)}</span>
          </button>`).join('')}
      </div>
      <div class="set-row no-border">
        <span class="grow"><span class="title">Анимации темы</span>
        <span class="sub">Появление, нажатия, кнопка питания</span></span>
        <button class="switch ${bool(state.animations) ? 'on' : ''}" data-action="switch"
                data-key="theme_animations" data-value="${bool(state.animations)}"></button>
      </div>
      <button class="btn primary wide" data-action="apply-theme">Применить · ${esc((THEMES.find(t => t.id === pendingTheme) || {}).name || '')}</button>
      <div class="sheet-gap"></div>`);
  }

  function openStrategySheet() {
    const items = state.strategies || [];
    openSheet(`
      <div class="sheet-head">
        <h3>Стратегия</h3>
        <button class="icon-btn" data-action="close-sheet">✕</button>
      </div>
      <div class="list sheet-list">
        ${items.length ? items.map(s => `
          <button class="row-card strategy ${s.cmd === state.strategyCmd ? 'selected' : ''}"
                  data-action="strategy" data-cmd="${esc(s.cmd)}">
            <span class="grow"><span class="title">${esc(s.name || 'Без названия')}</span>
            <span class="sub mono">${esc(s.cmd)}</span></span>
            <span class="mark">${s.cmd === state.strategyCmd ? '✓' : ''}</span>
          </button>`).join('') : '<div class="empty"><p>Стратегий пока нет</p></div>'}
      </div>
      <button class="btn ghost wide" data-action="open" data-screen="editor">Открыть редактор</button>
      <div class="sheet-gap"></div>`);
  }

  function openLicense() {
    openSheet(`
      <div class="sheet-head">
        <h3>Лицензия</h3>
        <button class="icon-btn" data-action="close-sheet">✕</button>
      </div>
      <div class="license">
        <p><b>ByeDPI Android</b> распространяется по лицензии
        <b>GNU General Public License v3.0</b>.</p>
        <p class="muted">Вы можете использовать, изучать, изменять и распространять код
        на условиях GPL-3.0. Полный текст лицензии — в файле LICENSE проекта.</p>
      </div>
      <button class="btn ghost wide" data-action="open-url" data-url="https://www.gnu.org/licenses/gpl-3.0.html">
        Открыть текст лицензии</button>
      <div class="sheet-gap"></div>`);
  }

  /* ---------- events ---------- */

  document.addEventListener('click', (e) => {
    const tabBtn = e.target.closest('#tabbar .tab');
    if (tabBtn) { switchTab(tabBtn.dataset.tab); return; }

    if (e.target.closest('#btnSettings')) { switchTab('settings'); return; }
    if (e.target.closest('#powerBtn')) {
      e.target.closest('#powerBtn').classList.add('busy');
      call('toggle');
      setTimeout(refresh, 900);
      return;
    }
    if (e.target.closest('#homeStrategy') || e.target.closest('[data-action="strategy-sheet"]')) {
      openStrategySheet(); return;
    }
    if (e.target.closest('#themeCard')) { openThemeSheet(); return; }

    const themeTab = e.target.closest('#themeTabs button');
    if (themeTab) { themeGroup = themeTab.dataset.group; renderThemeSheet(); return; }

    const act = e.target.closest('[data-action]');
    if (!act) return;
    const action = act.dataset.action;

    if (action === 'close-sheet') { closeSheet(); return; }
    if (action === 'back') {
      const cur = state && state.tab;
      switchTab(cur === 'tune' || cur === 'editor' ? 'strategies' : 'home');
      return;
    }
    if (action === 'apply-best') {
      const cmd = act.dataset.cmd;
      if (!cmd) return;
      call('applyStrategy', cmd);
      if (state) state.strategyCmd = cmd;
      renderStrategies();
      call('toast', 'Лучшая стратегия применена');
      return;
    }
    if (action === 'pick-theme') {
      pendingTheme = act.dataset.theme;
      renderThemeSheet(); return;
    }
    if (action === 'apply-theme') {
      call('setPref', 'app_theme', pendingTheme);
      closeSheet(); return;
    }
    if (action === 'apply-row') {
      const cmd = act.dataset.cmd;
      if (!cmd) return;
      call('applyStrategy', cmd);
      if (state) state.strategyCmd = cmd;
      renderStrategies();
      call('toast', 'Стратегия применена');
      return;
    }
    if (action === 'apply-strategy') {
      const cmd = act.dataset.cmd;
      if (!cmd) return;
      call('applyStrategy', cmd);
      if (state) state.strategyCmd = cmd;
      renderStrategies();
      $('inlineStatus').textContent = 'Стратегия применена';
      call('toast', 'Стратегия применена');
      return;
    }
    if (action === 'start-tune') {
      switchTab('tune');
      call('startTune');
      setTimeout(refresh, 700);
      return;
    }
    if (action === 'open') {
      const screen = act.dataset.screen;
      closeSheet();
      if (screen === 'test') { switchTab('tune'); return; }
      if (screen === 'editor') { switchTab('editor'); return; }
      if (screen === 'appearance') { openThemeSheet(); return; }
      call('open', screen); return;
    }
    if (action === 'preset') { $('cmdInput').value = act.dataset.cmd || ''; return; }
    if (action === 'dns-custom') {
      $('dnsCustom').hidden = false;
      $('dnsInput').value = state.dnsIp || '';
      $('dnsInput').focus();
      return;
    }
    if (action === 'license') { closeSheet(); openLicense(); return; }
    if (action === 'open-url') {
      call('openUrl', act.dataset.url); return;
    }
    if (action === 'mode') {
      call('setPref', 'byedpi_mode', act.dataset.mode);
      setTimeout(refresh, 150); return;
    }
    if (action === 'dns') {
      call('setPref', 'dns_ip', act.dataset.ip);
      setTimeout(refresh, 150); return;
    }
    if (action === 'switch') {
      const next = act.dataset.value !== 'true';
      act.dataset.value = String(next);
      act.classList.toggle('on', next);
      call('setPref', act.dataset.key, String(next));
      if (act.dataset.key === 'theme_animations' && state) {
        state.animations = next;
        document.body.classList.toggle('no-anim', !next);
      }
      setTimeout(refresh, 200); return;
    }
    if (action === 'strategy') {
      call('applyStrategy', act.dataset.cmd);
      closeSheet();
      setTimeout(refresh, 250); return;
    }
    if (action === 'app') {
      const pkg = act.dataset.pkg;
      if (selectedApps.has(pkg)) selectedApps.delete(pkg); else selectedApps.add(pkg);
      act.classList.toggle('selected', selectedApps.has(pkg));
      act.querySelector('.check').textContent = selectedApps.has(pkg) ? '✓' : '';
      // Choosing apps means "process only these" unless the user is on the
      // blacklist screen, where the selected apps are the ones that bypass.
      if (state.filterMode !== 'blacklist') {
        state.filterMode = selectedApps.size > 0 ? 'whitelist' : 'disable';
      }
      document.querySelectorAll('#appsMode button').forEach(b => {
        b.classList.toggle('active', b.dataset.mode === state.filterMode);
      });
      $('appsHint').textContent = state.filterMode === 'whitelist'
        ? 'Выбрано приложений: ' + selectedApps.size + ' · остальные идут напрямую'
        : state.filterMode === 'blacklist'
          ? 'Выбрано напрямую: ' + selectedApps.size + ' · остальные обрабатываются'
          : 'Обрабатываются все приложения';
      return;
    }
  });

  document.addEventListener('click', (e) => {
    if (e.target.id === 'overlay') closeSheet();
  });

  $('appsApply').addEventListener('click', () => {
    const mode = state.filterMode === 'whitelist' ? 'whitelist'
      : state.filterMode === 'blacklist' ? 'blacklist' : 'disable';
    call('setApps', JSON.stringify(Array.from(selectedApps)), mode);
    call('toast', 'Приложения сохранены');
    setTimeout(refresh, 400);
  });

  document.querySelectorAll('#appsMode button').forEach(btn => btn.addEventListener('click', () => {
    if (btn.dataset.mode === 'whitelist' && selectedApps.size === 0) {
      call('toast', 'Сначала выберите приложения');
      return;
    }
    call('setPref', 'applist_type', btn.dataset.mode);
    setTimeout(refresh, 150);
  }));

  // Quick presets from the "circle of trust" mockups: pick the apps people
  // actually unblock (TikTok, Telegram…) without scrolling a 400-item list.
  const QUICK_APPS = {
    tiktok: ['com.zhiliaoapp.musically', 'com.zhiliaoapp.musically.go', 'com.ss.android.ugc.trill'],
    telegram: ['org.telegram.messenger', 'org.telegram.messenger.web', 'org.thunderdog.challegram'],
    youtube: ['com.google.android.youtube', 'com.google.android.apps.youtube.music'],
    '2gis': ['ru.dublgis.dgismobile'],
    anixart: ['com.swiftsoft.anixartd', 'com.swiftsoft.anixart']
  };
  document.querySelectorAll('[data-quick]').forEach(chip => chip.addEventListener('click', () => {
    const pkgs = QUICK_APPS[chip.dataset.quick] || [];
    const installed = pkgs.filter(p => (state.apps || []).some(a => a.pkg === p));
    if (!installed.length) {
      call('toast', 'Приложение не установлено');
      return;
    }
    // A quick preset always means «пустить это приложение через обход». Even if
    // it was previously in the «Все, кроме» list, switch the mode to «Только
    // выбранные» — otherwise the app keeps bypassing the proxy and stays
    // blocked (that is exactly what happened with TikTok).
    const allSelected = installed.every(p => selectedApps.has(p)) &&
      state.filterMode === 'whitelist';
    installed.forEach(p => { if (allSelected) selectedApps.delete(p); else selectedApps.add(p); });
    state.filterMode = selectedApps.size ? 'whitelist' : 'disable';
    renderApps();
    call('setApps', JSON.stringify(Array.from(selectedApps)), state.filterMode);
    call('toast', allSelected ? 'Приложение убрано' : 'Добавлено в обход');
    setTimeout(refresh, 400);
  }));

  $('appsSearch').addEventListener('input', (e) => {
    appsQuery = e.target.value || '';
    renderApps();
  });

  $('appsList').addEventListener('scroll', () => {
    if (iconBudget > 0) fillIcons();
  }, { passive: true });

  $('tuneStart').addEventListener('click', () => call('startTune'));
  $('tuneStop').addEventListener('click', () => call('stopTune'));

  $('inlineStart').addEventListener('click', () => {
    call('startTune');
    if (tuneState) { tuneState.running = true; renderInlineTune(); }
    setTimeout(refresh, 600);
  });
  $('inlineStop').addEventListener('click', () => {
    call('stopTune');
    if (tuneState) { tuneState.stopping = true; renderInlineTune(); }
    setTimeout(refresh, 600);
  });

  $('dnsSave').addEventListener('click', () => {
    const ip = $('dnsInput').value.trim();
    if (ip) call('setPref', 'dns_ip', ip);
    $('dnsCustom').hidden = true;
    refresh();
  });

  $('cmdApply').addEventListener('click', () => {
    const cmd = $('cmdInput').value.trim();
    if (!cmd) { call('toast', 'Команда пустая'); return; }
    call('setCmd', cmd);
    call('toast', 'Команда применена');
    setTimeout(refresh, 300);
  });
  $('cmdClear').addEventListener('click', () => { $('cmdInput').value = ''; });
  $('cmdPaste').addEventListener('click', () => {
    const text = call('clipboard') || '';
    if (!text) { call('toast', 'Буфер обмена пуст'); return; }
    $('cmdInput').value = text;
  });

  $('settingsSearch').addEventListener('input', applySettingsSearch);

  /* ---------- boot ---------- */

  function boot() {
    const raw = call('state');
    if (raw) {
      applyState(raw);
      const saved = (state && state.tab) || 'home';
      // The state we just applied is fresh; avoid a second bridge round-trip
      // and a second render during startup.
      tab = (saved === 'tune' || saved === 'editor') ? 'strategies' : saved;
      if (state) state.tab = tab;
      call('setTab', tab);
      render();
    } else {
      applyState(demoState());
      switchTab('home');
    }
  }

  let tuneQueued = false;
  let lastTuneRefresh = 0;
  let wasTuning = false;

  window.UI = {
    refresh: (s) => applyState(s),
    tune: (raw) => {
      try { tuneState = typeof raw === 'string' ? JSON.parse(raw) : raw; }
      catch (e) { console.error(e); return; }
      const runningNow = !!tuneState.running;
      // Once the run finishes, pull the saved state so the picker shows the
      // final percentages and the winning strategy without a manual refresh.
      if (wasTuning && !runningNow) setTimeout(refresh, 150);
      wasTuning = runningNow;
      // Keep the persistent state in sync while testing; live per-strategy
      // counts below come straight from the test push itself.
      if (runningNow && tab === 'strategies' &&
          Date.now() - lastTuneRefresh > 2500) {
        lastTuneRefresh = Date.now();
        setTimeout(refresh, 60);
      }
      if (tuneQueued) return;
      tuneQueued = true;
      setTimeout(() => {
        tuneQueued = false;
        if (tab === 'tune') renderTune();
        if (tab === 'strategies' || tab === 'tune') {
          renderStrategies();
          renderInlineTune();
        }
      }, 300);
    },
    back: () => {
      if ($('sheet').classList.contains('open')) { closeSheet(); return true; }
      if (tab === 'tune' || tab === 'editor') { switchTab('strategies'); return true; }
      if (tab !== 'home') { switchTab('home'); return true; }
      return false;
    }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
