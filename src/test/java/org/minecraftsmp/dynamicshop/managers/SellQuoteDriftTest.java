package org.minecraftsmp.dynamicshop.managers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Sell-menu quotes must survive the tiny price drift of items whose shortage time keeps growing. */
public class SellQuoteDriftTest {

    @Test
    public void shortageDriftBetweenReviewAndConfirmStillMatches() {
        assertTrue(SellBasketService.Quote.sameValue(71.10, 71.10));
        assertTrue(SellBasketService.Quote.sameValue(71.10, 71.1000004));
        assertTrue(SellBasketService.Quote.sameValue(0.05, 0.059));
        assertTrue(SellBasketService.Quote.sameValue(25000.0, 25024.0));
    }

    @Test
    public void realPriceChangesStillRequireANewReview() {
        assertFalse(SellBasketService.Quote.sameValue(71.10, 71.20));
        assertFalse(SellBasketService.Quote.sameValue(0.05, 0.07));
        assertFalse(SellBasketService.Quote.sameValue(25000.0, 25030.0));
        assertFalse(SellBasketService.Quote.sameValue(10.0, Double.NaN));
    }
}
