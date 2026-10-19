package org.minecraftsmp.dynamicshop.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.bukkit.Material;
import org.junit.After;
import org.junit.Test;

/** On Folia, regions trade in parallel; concurrent stock updates must never be lost. */
public class ShopDataManagerConcurrencyTest {
    private static final Material ITEM = Material.REDSTONE;

    @After
    public void tearDown() {
        ShopDataManager.stockMap.remove(ITEM);
    }

    @Test
    public void parallelBuysAndSellsKeepEveryStockChange() throws Exception {
        ShopDataManager.setStockDirect(ITEM, 0);
        int threads = 8;
        int tradesPerThread = 5000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            double delta = t % 2 == 0 ? 3 : -1;
            results.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < tradesPerThread; i++) {
                    ShopDataManager.updateStock(ITEM, delta);
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> result : results) {
            result.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        double expected = (threads / 2) * tradesPerThread * 3.0 - (threads / 2) * tradesPerThread * 1.0;
        assertEquals(expected, ShopDataManager.getStock(ITEM), 0.0);
    }
}
