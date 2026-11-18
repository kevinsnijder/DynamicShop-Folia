package org.minecraftsmp.dynamicshop.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

/** EvenMoreFish fish pay their fixed worth, rounded like other sales, and never sell for nothing. */
public class EvenMoreFishWrapperTest {

    @After
    public void tearDown() {
        ConfigCacheManager.wholeCoins = false;
    }

    @Test
    public void wholeCoinsRoundDownButAFishWorthAnythingPaysAtLeastOneCoin() {
        ConfigCacheManager.wholeCoins = true;
        assertEquals(10.0, EvenMoreFishWrapper.unitValue(10.46, 1.0), 0.0);
        assertEquals(1.0, EvenMoreFishWrapper.unitValue(0.58, 1.0), 0.0);
        assertEquals(20.0, EvenMoreFishWrapper.unitValue(10.46, 2.0), 0.0);
    }

    @Test
    public void worthlessOrInvalidFishAreNotSellable() {
        ConfigCacheManager.wholeCoins = true;
        assertEquals(0.0, EvenMoreFishWrapper.unitValue(0.0, 1.0), 0.0);
        assertEquals(0.0, EvenMoreFishWrapper.unitValue(-5.0, 1.0), 0.0);
        assertEquals(0.0, EvenMoreFishWrapper.unitValue(Double.NaN, 1.0), 0.0);
        assertEquals(0.0, EvenMoreFishWrapper.unitValue(10.0, 0.0), 0.0);
    }

    @Test
    public void fractionalModeKeepsTheExactWorth() {
        assertEquals(0.58, EvenMoreFishWrapper.unitValue(0.58, 1.0), 1e-9);
    }

    @Test
    public void fishKeysAreRecognisedAndNothingIsAFishWithoutEvenMoreFish() {
        assertTrue(EvenMoreFishWrapper.isFishKey("EMF:Rare:Nemo"));
        assertFalse(EvenMoreFishWrapper.isFishKey("COD"));
        assertFalse(EvenMoreFishWrapper.isFishKey(null));
        assertNull(EvenMoreFishWrapper.find(null));
    }
}
