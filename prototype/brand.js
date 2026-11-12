const CRESCENT = (bg, fg, dot, scale) => `
<svg viewBox="0 0 512 512" fill="none" aria-hidden="true">
  ${bg ? `<rect width="512" height="512" fill="${bg}"/>` : ''}
  <g transform="translate(256 256) scale(${scale || 1}) translate(-256 -256)">
    <g>
      <path d="M438 146C338 108 104 164 104 258C104 352 300 408 378 370C230 330 214 182 438 146Z" fill="${fg}"/>
      <circle cx="382" cy="256" r="21" fill="${dot}"/>
    </g>
  </g>
</svg>`;

const BRAND_MARK = CRESCENT(null, '#F5F5F5', '#4F8DFD');
const BRAND_ICON = CRESCENT('#000000', '#F5F5F5', '#4F8DFD', 1.14);
const BRAND_ICON_BLUE = CRESCENT('#2F6BD9', '#FFFFFF', '#111111', 1.14);
const BRAND_MONO = CRESCENT(null, '#FFFFFF', '#FFFFFF');

document.querySelectorAll('[data-logo]').forEach(function (el) {
  el.innerHTML = BRAND_MARK;
});
document.querySelectorAll('[data-app-icon]').forEach(function (el) {
  el.innerHTML = BRAND_ICON;
});
window.CRESCENT = CRESCENT;
window.BRAND_MARK = BRAND_MARK;
window.BRAND_ICON = BRAND_ICON;
window.BRAND_ICON_BLUE = BRAND_ICON_BLUE;
window.BRAND_MONO = BRAND_MONO;
