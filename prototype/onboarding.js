(function () {
  const steps = Array.from(document.querySelectorAll('.step'));
  const dots = Array.from(document.querySelectorAll('#progress i'));
  const labels = ['Continue', 'Continue', 'Sign in with Upwork', 'Continue', 'Start watching'];
  const meta = [
    'Step 1 of 5',
    'Step 2 of 5',
    'Step 3 of 5',
    'Step 4 of 5',
    'Step 5 of 5'
  ];
  const back = document.getElementById('back');
  const next = document.getElementById('next');
  const skip = document.getElementById('skip');
  const metaEl = document.getElementById('meta');
  let index = 0;
  let connected = false;

  function render() {
    steps.forEach(function (step, i) {
      step.classList.toggle('active', i === index);
    });
    dots.forEach(function (dot, i) {
      dot.classList.toggle('on', i === index);
      dot.classList.toggle('done', i < index);
    });
    back.disabled = index === 0;
    next.textContent = index === 2 && !connected ? 'Sign in with Upwork' : labels[index];
    metaEl.textContent = index === 2 && connected ? 'Connected' : meta[index];
    next.style.background = index === 2 && connected ? '' : '';
    document.querySelector('.guide-body').scrollTop = 0;
  }

  next.addEventListener('click', function () {
    if (index === 2 && !connected) {
      connected = true;
      showToast('Connected to Upwork · read-only');
      render();
      return;
    }
    if (index === steps.length - 1) {
      location.href = 'alerts.html';
      return;
    }
    index += 1;
    render();
  });

  back.addEventListener('click', function () {
    if (index > 0) {
      index -= 1;
      render();
    }
  });

  skip.addEventListener('click', function () {
    location.href = 'alerts.html';
  });

  render();
})();
