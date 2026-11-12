(function () {
  const params = new URLSearchParams(location.search);
  const flow = params.has('flow');
  const seek = params.get('t');

  const mark = document.getElementById('mark');
  const ball = document.getElementById('ball');
  const up = document.getElementById('up');
  const roc = document.getElementById('roc');
  const tagline = document.getElementById('tagline');
  const skip = document.getElementById('skip');
  const splash = document.getElementById('splash');
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const go = function () { location.href = 'onboarding.html'; };

  if (reduced) {
    mark.style.width = '64px';
    mark.style.height = '64px';
    mark.style.transform = 'translate(-50%,-50%) rotate(360deg)';
    ball.style.transform = 'translate(0,0)';
    up.style.cssText += 'opacity:1;font-size:40px;transform:translate(0,-50%)';
    roc.style.cssText += 'opacity:1;font-size:40px;transform:translate(0,-50%)';
    tagline.style.opacity = '1';
    skip.style.opacity = '1';
    if (flow) setTimeout(go, 2200);
    splash.addEventListener('click', go);
    return;
  }

  // 1. ball thrown in from outside, into the bite
  ball.animate([
    { transform: 'translate(1280px,1280px)' },
    { transform: 'translate(0,0)' }
  ], { duration: 700, delay: 200, easing: 'cubic-bezier(.45,0,.2,1)', fill: 'both' });

  // 2. crescent + ball spin 315° to 0
  mark.animate([
    { transform: 'translate(-50%,-50%) rotate(-45deg)' },
    { transform: 'translate(-50%,-50%) rotate(-360deg)' }
  ], { duration: 1500, delay: 900, easing: 'cubic-bezier(.22,.61,.36,1)', fill: 'forwards' });

  // 3. icon shrinks into the word, up / roc close in from both sides
  mark.animate([
    { width: '200px', height: '200px' },
    { width: '64px', height: '64px' }
  ], { duration: 620, delay: 2400, easing: 'cubic-bezier(.2,.7,.2,1)', fill: 'forwards' });

  up.animate([
    { opacity: 0, transform: 'translate(-150px,-50%)', fontSize: '56px' },
    { opacity: 1, transform: 'translate(0,-50%)', fontSize: '40px' }
  ], { duration: 620, delay: 2400, easing: 'cubic-bezier(.2,.75,.2,1)', fill: 'forwards' });

  roc.animate([
    { opacity: 0, transform: 'translate(150px,-50%)', fontSize: '56px' },
    { opacity: 1, transform: 'translate(0,-50%)', fontSize: '40px' }
  ], { duration: 620, delay: 2400, easing: 'cubic-bezier(.2,.75,.2,1)', fill: 'forwards' });

  tagline.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 500, delay: 3060, fill: 'forwards' });
  skip.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 500, delay: 3060, fill: 'forwards' });

  if (seek !== null) {
    const t = Number(seek);
    document.getAnimations().forEach(function (animation) {
      animation.pause();
      animation.currentTime = t;
    });
  } else if (flow) {
    setTimeout(go, 5700);
  }

  splash.addEventListener('click', go);
})();
