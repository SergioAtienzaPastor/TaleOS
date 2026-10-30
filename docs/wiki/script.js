(() => {
  if ('scrollRestoration' in history) history.scrollRestoration = 'manual';
  const articles = [...document.querySelectorAll('.article')];
  const links = [...document.querySelectorAll('[data-target]')];
  const sidebar = document.querySelector('.sidebar');
  const toc = document.querySelector('.toc');
  const search = document.querySelector('#search');
  const results = document.querySelector('#search-results');

  function show(id, updateHash = false) {
    const article = document.getElementById(id) || document.getElementById('home');
    articles.forEach(item => item.classList.toggle('active', item === article));
    links.forEach(link => link.classList.toggle('active', link.dataset.target === article.id));
    document.title = `${article.dataset.title} · TaleOS Wiki`;
    if (updateHash && location.hash !== `#${article.id}`) history.pushState(null, '', `#${article.id}`);
    sidebar.classList.remove('open');
    results.classList.remove('open');
    search.blur();
    window.scrollTo(0, 0);
    requestAnimationFrame(() => window.scrollTo(0, 0));
    buildToc(article);
  }

  function buildToc(article) {
    toc.replaceChildren();
    const title = document.createElement('strong');
    title.textContent = 'On this page';
    toc.append(title);
    article.querySelectorAll('h2[id]').forEach(heading => {
      const link = document.createElement('a');
      link.href = `#${heading.id}`;
      link.textContent = heading.textContent;
      link.addEventListener('click', event => {
        event.preventDefault();
        heading.scrollIntoView({ behavior: 'smooth' });
      });
      toc.append(link);
    });
  }

  links.forEach(link => link.addEventListener('click', event => {
    event.preventDefault();
    show(link.dataset.target, true);
  }));
  document.querySelector('.menu-button').addEventListener('click', () => sidebar.classList.toggle('open'));
  window.addEventListener('hashchange', () => show(location.hash.slice(1) || 'home'));

  document.querySelectorAll('pre').forEach(pre => {
    const button = document.createElement('button');
    button.className = 'copy';
    button.type = 'button';
    button.textContent = 'Copy';
    button.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(pre.querySelector('code').textContent);
        button.textContent = 'Copied';
        setTimeout(() => { button.textContent = 'Copy'; }, 1200);
      } catch {
        button.textContent = 'Select text';
      }
    });
    pre.append(button);
  });

  const index = articles.map(article => ({
    id: article.id,
    title: article.dataset.title,
    group: article.dataset.group,
    text: article.textContent.replace(/\s+/g, ' ').trim(),
  }));

  search.addEventListener('input', () => {
    const query = search.value.trim().toLowerCase();
    results.replaceChildren();
    if (query.length < 2) {
      results.classList.remove('open');
      return;
    }
    const matches = index.filter(item => item.text.toLowerCase().includes(query)).slice(0, 12);
    if (!matches.length) {
      const empty = document.createElement('div');
      empty.className = 'search-empty';
      empty.textContent = 'No results found.';
      results.append(empty);
    }
    matches.forEach(item => {
      const link = document.createElement('a');
      link.className = 'search-result';
      link.href = `#${item.id}`;
      const position = item.text.toLowerCase().indexOf(query);
      const start = Math.max(0, position - 55);
      const snippet = item.text.slice(start, start + 170);
      link.innerHTML = `<strong></strong><small></small>`;
      link.querySelector('strong').textContent = item.title;
      link.querySelector('small').textContent = `${item.group} · ${start ? '…' : ''}${snippet}…`;
      link.addEventListener('click', event => {
        event.preventDefault();
        show(item.id, true);
        search.value = '';
      });
      results.append(link);
    });
    results.classList.add('open');
  });
  search.addEventListener('keydown', event => {
    if (event.key === 'Escape') {
      search.value = '';
      results.classList.remove('open');
    }
    if (event.key === 'Enter') {
      const first = results.querySelector('.search-result');
      if (first) first.click();
    }
  });
  document.addEventListener('click', event => {
    if (!event.target.closest('.search-wrap')) results.classList.remove('open');
  });
  document.addEventListener('keydown', event => {
    if (event.key === '/' && !/INPUT|TEXTAREA/.test(document.activeElement.tagName)) {
      event.preventDefault();
      search.focus();
    }
  });

  show(location.hash.slice(1) || 'home');
  window.addEventListener('load', () => {
    show(location.hash.slice(1) || 'home');
  }, { once: true });
})();
