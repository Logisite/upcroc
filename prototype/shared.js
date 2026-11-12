function showToast(message) {
  const toast = document.querySelector('.toast');
  if (!toast) return;
  toast.textContent = message;
  toast.classList.add('show');
  clearTimeout(toast._timer);
  toast._timer = setTimeout(() => toast.classList.remove('show'), 1800);
}
window.showToast = showToast;

document.addEventListener('click', function (event) {
  const switcher = event.target.closest('.switch');
  if (switcher) {
    switcher.classList.toggle('on');
    return;
  }

  const chip = event.target.closest('.chip');
  if (chip && chip.parentElement) {
    chip.parentElement.querySelectorAll('.chip').forEach(function (item) {
      item.classList.remove('active');
    });
    chip.classList.add('active');
    return;
  }

  const action = event.target.closest('[data-action]');
  if (!action) return;
  const name = action.dataset.action;

  if (name === 'toast') {
    showToast(action.dataset.msg || 'Done');
  } else if (name === 'bookmark') {
    action.classList.toggle('on');
    showToast(action.classList.contains('on') ? 'Job saved' : 'Removed from saved');
  } else if (name === 'open-sheet') {
    document.querySelector('.sheet-backdrop')?.classList.add('open');
    document.querySelector('.sheet')?.classList.add('open');
  } else if (name === 'close-sheet') {
    document.querySelector('.sheet-backdrop')?.classList.remove('open');
    document.querySelector('.sheet')?.classList.remove('open');
  } else if (name === 'save') {
    showToast('Search saved');
  } else if (name === 'check') {
    showToast('Checking Upwork now…');
    window.dispatchEvent(new CustomEvent('upcroc:check'));
  } else if (name === 'toggle-watch') {
    window.dispatchEvent(new CustomEvent('upcroc:watch'));
  }
});

const STATUS_GLYPHS = `
  <svg viewBox="0 0 18 12" fill="none" aria-hidden="true"><rect x="1" y="7" width="2.6" height="4" rx="1" fill="currentColor"/><rect x="5.2" y="5" width="2.6" height="6" rx="1" fill="currentColor"/><rect x="9.4" y="3" width="2.6" height="8" rx="1" fill="currentColor"/><rect x="13.6" y="1" width="2.6" height="10" rx="1" fill="currentColor"/></svg>
  <svg viewBox="0 0 16 12" fill="none" aria-hidden="true"><path d="M1.4 4.2a10 10 0 0113.2 0" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/><path d="M4 6.8a6.2 6.2 0 018 0" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/><circle cx="8" cy="10" r="1.1" fill="currentColor"/></svg>
  <svg class="bat" viewBox="0 0 24 12" fill="none" aria-hidden="true"><rect x="1" y="1.6" width="18.5" height="8.8" rx="2.6" stroke="currentColor" stroke-opacity=".55"/><rect x="3" y="3.6" width="12" height="4.8" rx="1.4" fill="currentColor"/><path d="M21.4 4.6v2.8" stroke="currentColor" stroke-opacity=".55" stroke-linecap="round"/></svg>`;

document.querySelectorAll('.status').forEach(function (el) {
  const time = el.dataset.time || '9:41';
  el.innerHTML = '<span class="time">' + time + '</span><span class="glyphs">' + STATUS_GLYPHS + '</span>';
});
