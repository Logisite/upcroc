(function () {
  const logs = Array.from(document.querySelectorAll('#logs .log'));
  const empty = document.getElementById('log-empty');
  let filter = 'all';

  function apply() {
    let visible = 0;
    logs.forEach(function (row) {
      const kind = row.dataset.kind;
      const show = filter === 'all'
        || kind === filter
        || (filter === 'error' && (kind === 'error' || kind === 'warn'));
      row.style.display = show ? '' : 'none';
      if (show) visible += 1;
    });
    empty.style.display = visible === 0 ? 'block' : 'none';
  }

  document.querySelectorAll('#log-filters button').forEach(function (button) {
    button.addEventListener('click', function () {
      document.querySelectorAll('#log-filters button').forEach(function (item) {
        item.classList.toggle('active', item === button);
      });
      filter = button.dataset.filter;
      apply();
    });
  });

  logs.forEach(function (row) {
    row.querySelector('.log-main').addEventListener('click', function () {
      row.classList.toggle('open');
    });
  });

  document.getElementById('battery-banner').querySelector('.link').addEventListener('click', function () {
    document.getElementById('battery-banner').style.display = 'none';
  });
})();
