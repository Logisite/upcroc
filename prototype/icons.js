const ICONS = {
  radar: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><circle cx="12" cy="12" r="8"/><circle cx="12" cy="12" r="4.2"/><circle cx="12" cy="12" r="1.3" fill="currentColor" stroke="none"/><path d="M12 4V2M12 22v-2M4 12H2M22 12h-2"/></svg>',
  search: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><circle cx="10.8" cy="10.8" r="6.3"/><path d="M15.5 15.5L21 21"/></svg>',
  settings: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><path d="M4 7h7M15 7h5"/><circle cx="13" cy="7" r="2"/><path d="M4 12h3M11 12h9"/><circle cx="9" cy="12" r="2"/><path d="M4 17h8M16 17h4"/><circle cx="14" cy="17" r="2"/></svg>',
  bell: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><path d="M6 9a6 6 0 0112 0c0 5 2 6 2 7H4c0-1 2-2 2-7z"/><path d="M10 20h4"/></svg>',
  signal: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><path d="M4 12h3l2-5 3 10 2-5h6"/></svg>',
  plus: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>',
  pause: '<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><rect x="7" y="5" width="3.4" height="14" rx="1.2"/><rect x="13.6" y="5" width="3.4" height="14" rx="1.2"/></svg>',
  play: '<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><path d="M8 5l11 7-11 7z"/></svg>',
  check: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M20 11a8 8 0 10-2.4 5.7"/><path d="M20 4v7h-7"/></svg>',
  checkmark: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.5 4.5L19 7"/></svg>',
  health: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><path d="M3 12h4l2.5-6 4 12L16 12h5"/></svg>',
  bookmark: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"><path d="M7 4h10a1 1 0 011 1v15l-6-3.2L6 20V5a1 1 0 011-1z"/></svg>',
  chevron: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><path d="M9 6l6 6-6 6"/></svg>',
  back: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M15 5l-7 7 7 7"/></svg>',
  close: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18"/></svg>',
  edit: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><path d="M4 20h4l10-10-4-4L4 16v4z"/><path d="M13 7l4 4"/></svg>',
  refresh: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><path d="M20 11a8 8 0 10-2.4 5.7"/><path d="M20 4v7h-7"/></svg>',
  warn: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M12 4l8.5 15h-17z"/><path d="M12 10v4.5"/><circle cx="12" cy="17" r=".9" fill="currentColor" stroke="none"/></svg>',
  error: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><circle cx="12" cy="12" r="8.2"/><path d="M9 9l6 6M15 9l-6 6"/></svg>',
  info: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><circle cx="12" cy="12" r="8.2"/><path d="M12 11v5"/><circle cx="12" cy="8" r=".9" fill="currentColor" stroke="none"/></svg>',
  key: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><circle cx="8" cy="12" r="4"/><path d="M12 12h9M18 12v3M15 12v2"/></svg>',
  shield: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"><path d="M12 3l7 2.5v6c0 4.4-3 7.6-7 9.5-4-1.9-7-5.1-7-9.5v-6z"/><path d="M8.8 12l2.2 2.2 4.2-4.4" stroke-linecap="round"/></svg>',
  lock: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><rect x="5" y="10.5" width="14" height="10" rx="2.5"/><path d="M8.5 10.5V8a3.5 3.5 0 017 0v2.5"/><circle cx="12" cy="15.5" r="1.1" fill="currentColor" stroke="none"/></svg>',
  battery: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><rect x="3" y="8" width="16" height="8" rx="2.5"/><path d="M21 11v2"/><path d="M6.5 11v2M9.5 11v2M12.5 11v2"/></svg>',
  moon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M20 14.5A8.2 8.2 0 019.5 4 8.2 8.2 0 1020 14.5z"/></svg>',
  sound: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M5 10v4h3l4 3.5v-11L8 10z"/><path d="M15.5 9.5a4 4 0 010 5M18 7a7.5 7.5 0 010 10"/></svg>',
  logs: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><path d="M5 6h14M5 11h9M5 16h12M5 21h6"/></svg>',
  download: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M12 4v10"/><path d="M8 10.5l4 4 4-4"/><path d="M5 19h14"/></svg>',
  external: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M14 4h6v6"/><path d="M20 4l-8 8"/><path d="M19 14v5a1 1 0 01-1 1H5a1 1 0 01-1-1V6a1 1 0 011-1h5"/></svg>',
  trash: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><path d="M5 7h14M10 7V5h4v2M7 7l1 12h8l1-12"/><path d="M10.5 11v5M13.5 11v5"/></svg>',
  copy: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"><rect x="9" y="9" width="11" height="11" rx="2"/><path d="M15 9V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7a2 2 0 002 2h3"/></svg>',
  share: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><circle cx="17" cy="6" r="2.5"/><circle cx="7" cy="12" r="2.5"/><circle cx="17" cy="18" r="2.5"/><path d="M9.2 10.8l5.6-3.4M9.2 13.2l5.6 3.4"/></svg>',
  filter: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M4 5h16l-6 7.5V19l-4 2v-8.5z"/></svg>',
  offline: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><path d="M3 4l18 16"/><path d="M8.5 8.6A11 11 0 013.5 10M20.5 10a13 13 0 00-4.2-2.3M6 13a8 8 0 013-1.6M18 13a8 8 0 00-2.4-1.4"/><circle cx="12" cy="18" r="1.2" fill="currentColor" stroke="none"/></svg>',
  clock: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><circle cx="12" cy="12" r="8.4"/><path d="M12 7.5V12l3 2"/></svg>',
  layers: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"><path d="M12 4l8 4.2-8 4.2-8-4.2z"/><path d="M4 12.6l8 4.2 8-4.2M4 16.6l8 4.2 8-4.2"/></svg>',
  logout: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M10 5H6a1 1 0 00-1 1v12a1 1 0 001 1h4"/><path d="M14 8l4 4-4 4M18 12H9"/></svg>',
  user: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><circle cx="12" cy="8" r="3.6"/><path d="M5 20c.8-3.6 3.5-5.4 7-5.4S18.2 16.4 19 20"/></svg>',
  bolt: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"><path d="M13 3L5 14h6l-1 7 8-11h-6z"/></svg>',
  star: '<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><path d="M12 3.6l2.5 5.1 5.6.8-4.1 4 1 5.6-5-2.6-5 2.6 1-5.6-4.1-4 5.6-.8z"/></svg>',
  calendar: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><rect x="4" y="6" width="16" height="14" rx="2.5"/><path d="M8 3.5v4M16 3.5v4M4 11h16"/></svg>',
  globe: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><circle cx="12" cy="12" r="8.4"/><path d="M3.6 12h16.8M12 3.6c2.4 2.3 3.6 5.1 3.6 8.4S14.4 18.1 12 20.4C9.6 18.1 8.4 15.3 8.4 12S9.6 5.9 12 3.6z"/></svg>',
  cursor: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"><path d="M6 3l12 8-5.4 1.4L10 18z"/></svg>'
};

function renderIcons(root) {
  (root || document).querySelectorAll('[data-icon]').forEach(function (el) {
    el.innerHTML = ICONS[el.dataset.icon] || '';
  });
}
window.ICONS = ICONS;
window.renderIcons = renderIcons;
renderIcons();
