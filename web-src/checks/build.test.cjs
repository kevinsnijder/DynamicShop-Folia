const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { buildWebsite } = require('../build.cjs');

const source = name => fs.readFileSync(path.join(__dirname, '..', name), 'utf8');
const changedFiles = (before, after) => Object.keys(before).filter(file => before[file] !== after[file]).sort();

test('plugin-version labels, BOM, and platform line endings do not change the website', () => {
  const baseline = buildWebsite();
  for (const version of ['3.0.2', '3.0.3', '4.0.0']) {
    const built = buildWebsite(name => '\uFEFF' + source(name).replace(/^\uFEFF/, '')
      .replaceAll('@project.version@', version).replace(/\r?\n/g, '\r\n'));
    assert.deepEqual(built, baseline);
  }
});

test('script and stylesheet changes update their revisions and browser cache keys', () => {
  const baseline = buildWebsite();
  for (const [input, output] of [['catalog.js', 'app.js'], ['dsx.css', 'style.css']]) {
    const built = buildWebsite(name => source(name) + (name === input ? '\n/* Changed content */\n' : ''));
    assert.deepEqual(changedFiles(baseline, built), [output, 'index.html'].sort());
    assert.notEqual(baseline[output].split('\n')[0], built[output].split('\n')[0]);
    assert.notEqual(baseline['index.html'].split('\n')[0], built['index.html'].split('\n')[0]);
  }
});

test('an HTML-only change leaves all other bundled files unchanged', () => {
  const baseline = buildWebsite();
  for (const input of ['index.html', 'web-update.html']) {
    const built = buildWebsite(name => source(name) + (name === input ? '\n<!-- Changed content -->\n' : ''));
    assert.deepEqual(changedFiles(baseline, built), [input]);
  }
  assert.deepEqual(buildWebsite(), baseline);
});
