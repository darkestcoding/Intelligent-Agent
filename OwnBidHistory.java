/*
 * OwnBidHistory class
 */
package group5;

import genius.core.Bid;
import genius.core.utility.AdditiveUtilitySpace;

import java.util.ArrayList;

/**
 *
 * @author Justin
 */
public class OwnBidHistory {

    private ArrayList<Bid> BidHistory = new ArrayList<Bid>();
    private Bid minBidInHistory;

    public void addBid(Bid bid, AdditiveUtilitySpace utilitySpace) {
        if (BidHistory.indexOf(bid) == -1) {
            BidHistory.add(bid);
        }
        try {
            if (BidHistory.size() == 1) {
                this.minBidInHistory = BidHistory.get(0);
            } else {
                if (utilitySpace.getUtility(bid) < utilitySpace.getUtility(this.minBidInHistory)) {
                    this.minBidInHistory = bid;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected Bid getMinBidInHistory() {
        return this.minBidInHistory;
    }

    protected Bid getLastBid() {
        if (BidHistory.size() >= 1) {
            return BidHistory.get(BidHistory.size() - 1);
        } else {
            return null;
        }
    }

    public int numOfBidsProposed() {
        return BidHistory.size();
    }

    /* 这个目前用不着
    protected Bid chooseLowestBidInHistory(AdditiveUtilitySpace utilitySpace) {
        double minUtility = 100;
        Bid minBid = null;
        try {
            for (Bid bid : BidHistory) {
                if (utilitySpace.getUtility(bid) < minUtility) {
                    minUtility = utilitySpace.getUtility(bid);
                    minBid = bid;
                }
            }
        } catch (Exception e) {
            System.out.println("Exception in chooseLowestBidInHistory");
        }
        return minBid;
    }*/
}
