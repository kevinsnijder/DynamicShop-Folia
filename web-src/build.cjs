const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');

const digest = text => crypto.createHash('sha256').update(text).digest('hex');
const content = text => text.replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n')
  .replace(/^(?:\/\/|\/\*|<!--)\s*DS-WEB-VERSION:[^\n]*(?:\n|$)/, '');

function buildWebsite(load = name => fs.readFileSync(path.join(__dirname, name), 'utf8')) {
  const read = name => content(load(name));
  const admin = read('admin.js').replace('/* APPEARANCE_UI */', () => read('appearance.js'))
    .replace('/* LIVE_ACTIONS */', () => read('admin-actions.js'))
    .replace('/* LIVE_AUTH */', () => read('admin-auth.js'));
  const script = [read('catalog.js'), read('storefront.js'), read('activity.js'), read('nova.js'),
    read('usage.js'), read('dsx.js'), admin].join('\n');
  const css = read('style.css') + '\n' + read('dsx.css');
  const html = read('index.html').replace(/app\.js\?v=[^"']+/, 'app.js?v=' + digest(script).slice(0, 12))
    .replace(/style\.css\?v=[^"']+/, 'style.css?v=' + digest(css).slice(0, 12));
  const files = { 'app.js': script, 'index.html': html, 'style.css': css };
  // Old bookmarks and operator registration links continue to reach the same application.
  for (const [file, route] of [['admin.html', 'settings'], ['items.html', 'catalog']]) {
    files[file] = '<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="no-referrer"><title>DynamicShop</title></head><body><p>Opening DynamicShop… <a href="index.html#' + route + '">Continue</a></p><script>location.replace("index.html"+location.search+"#' + route + '");</script></body></html>';
  }
  for (const file of ['dashboard.js', 'items.js']) {
    files[file] = '// Compatibility entry: the 3.0 website uses app.js.\n';
  }
  files['web-update.html'] = read('web-update.html');
  for (const [file, body] of Object.entries(files)) {
    const revision = 'DS-WEB-VERSION: sha256-' + digest(body);
    const marker = file.endsWith('.html') ? '<!-- ' + revision + ' -->'
      : file.endsWith('.css') ? '/* ' + revision + ' */' : '// ' + revision;
    files[file] = marker + '\n' + body;
  }
  return files;
}

if (require.main === module) {
  const out = path.join(__dirname, '../src/main/resources/web');
  fs.mkdirSync(out, { recursive: true });
  for (const [file, body] of Object.entries(buildWebsite())) fs.writeFileSync(path.join(out, file), body);
  console.log('Built production website (six designs, content-based revisions).');
}

module.exports = { buildWebsite };
