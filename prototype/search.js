document.querySelectorAll('[data-seg]').forEach(function (button) {
  button.addEventListener('click', function () {
    const target = button.dataset.seg;
    document.querySelectorAll('[data-seg]').forEach(function (item) {
      item.classList.toggle('active', item === button);
    });
    document.querySelectorAll('[data-panel]').forEach(function (panel) {
      panel.style.display = panel.dataset.panel === target ? 'block' : 'none';
    });
  });
});
