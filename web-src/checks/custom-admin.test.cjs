const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../admin.js'), 'utf8');
const load = source.slice(source.indexOf('  async function loadAdmin(){'), source.indexOf('  async function refresh(){'));

test('category editor includes physical custom entries with exact IDs and excludes rewards', async () => {
  const rawItems = [
    { item: 'DIAMOND', category: 'MISC', basePrice: 10 },
    { item: 'special:pack_Iridium', material: 'GOLDEN_HORSE_ARMOR', category: 'CUSTOM_1', tradeItem: true, basePrice: 250, stock: 12, sellDisabled: true },
    { item: 'special:rank', category: 'PERMISSIONS', basePrice: 100 },
    { item: 'playershop:123', category: 'PLAYER_SHOPS', basePrice: 100 }
  ];
  const entries = [
    { id: 'pack_Iridium', type: 'server', tradeItem: true },
    { id: 'spawner', type: 'server' },
    { id: 'rank', type: 'perm' }
  ];
  const context = vm.createContext({
    items: [], cats: [], defaults: [], state: {},
    mapItem: i => ({ ...i, id: i.item }),
    $: () => ({}), esc: s => s,
    api: async url => ({
      '/api/admin/items': rawItems,
      '/api/admin/categories': [],
      '/api/admin/config': {},
      '/api/admin/entries': { entries, listings: [] },
      '/api/admin/audit': []
    })[url]
  });
  vm.runInContext(load, context);
  await context.loadAdmin();
  assert.deepEqual(Array.from(context.items, i => i.id), ['DIAMOND', 'special:pack_Iridium']);
  const custom = context.items[1];
  assert.equal(custom.adminCategory, 'CUSTOM_1');
  assert.equal(custom.stock, 12);
  assert.equal(custom.sellDisabled, true);
  assert.equal(custom.material, 'golden_horse_armor');
  assert.deepEqual(Array.from(context.state.serverItems, i => i.id), ['spawner']);
});
