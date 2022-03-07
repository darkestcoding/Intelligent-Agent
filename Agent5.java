package group5;

import genius.core.*;
import genius.core.actions.*;
import genius.core.issue.*;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.uncertainty.BidRanking;
import genius.core.uncertainty.UserModel;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class Agent5 extends AbstractNegotiationParty {

    // opponent model
    private double totalTime;
    private Action ActionOfOpponent = null;
    private double maximumOfBid;
    private OwnBidHistory ownBidHistory = new OwnBidHistory();
    private OpponentBidHistory opponentBidHistory = new OpponentBidHistory();
    private double minimumUtilityThreshold;
    private double utilitythreshold;
    private double MaximumUtility;
    private double timeLeftBefore = 0d;
    private double timeLeftAfter;
    // 对手出一次标需要的最多时间
    private double maximumTimeOfOpponent = 0d;
    // 自己出一次标需要的最多时间
    private double maximumTimeOfOwn = 0d;
    private double discountingFactor;
    // concedeToDiscountingFactor
    private double concedeToDF;
    // original concedeToDiscountingFactor
    private double concedeToDFOriginal;
    private double minconcedeToDF = 0.08;// 0.1;
    private ArrayList<ArrayList<Bid>> bidsBetweenUtility = new ArrayList<>();
    private boolean concedeToOpponent;
    private boolean toughAgent; // if we propose a bid that was proposed by the
    // opponnet, then it should be accepted.
    private double alpha1 = 2;// the larger alpha is, the more tough the agent is.
    private Bid bid_maximum_utility;// the bid with the maximum utility over the
    // utility space.
    private double reservationValue;
    //    private Random rand = new Random();
    // utility区间的间隔，给个常量0.01，可以方便改
    private float utilityInterval = 0.01f;

    // userModel
    private AbstractUtilitySpace predictAbstractSpace;
    private AdditiveUtilitySpace predictAdditiveSpace;
    private Bid maxBidForMe;

    // common
    private AgentID agentId;
    private int round = 0;

    @Override
    public void init(NegotiationInfo info) {
        super.init(info);
        try {
            // user model
            if (hasPreferenceUncertainty()) {
                //初始化自己的模型
                GeneticAlgorithm geneticAlgorithm = new GeneticAlgorithm(userModel);
                this.predictAbstractSpace = geneticAlgorithm.runGeneticAlgorithm();
                //返回一个效用空间，这个累加效用空间，近似于当前的不确定效用空间
                this.predictAdditiveSpace = (AdditiveUtilitySpace) predictAbstractSpace;
                this.utilitySpace = this.predictAdditiveSpace;
                BidRanking bidRanking = userModel.getBidRanking();
                //先获得自己最高的bid用来迷惑对手
                this.maxBidForMe = bidRanking.getMaximalBid();
                //用于关闭所有打印输出
                System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("initialization usermodel error" + e.getMessage());
        }

        // opponent model
        try {
            maximumOfBid = this.utilitySpace.getDomain().getNumberOfPossibleBids();
            this.bid_maximum_utility = utilitySpace.getMaxUtilityBid();
            this.utilitythreshold = utilitySpace.getUtility(bid_maximum_utility); // initial utility threshold
            this.MaximumUtility = this.utilitythreshold;
            this.timeLeftAfter = timeline.getCurrentTime();
            this.totalTime = timeline.getTotalTime();
            utilitySpace.setDiscount(0.77);
            this.discountingFactor = utilitySpace.getDiscountFactor();
            this.chooseUtilityThreshold();
            this.calculateBidsBetweenUtility();
            this.chooseConcedeToDiscountingDegree();
            this.opponentBidHistory.initializeDataStructures(utilitySpace.getDomain());
            this.concedeToOpponent = false;
            this.toughAgent = false;
            this.reservationValue = utilitySpace.getReservationValue();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("initialization opponentmodel error" + e.getMessage());
        }
        // common
        this.agentId = info.getAgentID();
    }

    @Override
    public void receiveMessage(AgentID sender, Action opponentAction) {
        this.ActionOfOpponent = opponentAction;
    }

    /*@Override
    public String getVersion() {
        return "MyAgent_version_1";
    }*/

   /* @Override
    public String getName() {
        return "MyAgent";
    }*/

    // @override
    @Override
    public Action chooseAction(List<Class<? extends Action>> possibleActions) {
        Action action = null;
        try {
            this.timeLeftBefore = timeline.getCurrentTime();
            Bid bid = null;

            if (ActionOfOpponent == null || ActionOfOpponent instanceof Inform) {
                // we propose first and propose the bid with maximum utility
                bid = this.bid_maximum_utility;
                action = new Offer(agentId, bid);
                System.out.println("we propose first and propose the bid with maximum utility");
            } else if (ActionOfOpponent instanceof Offer) {
                // the opponent propose first and we response secondly receiveMessage opponent model first
                this.opponentBidHistory.updateOpponentModel(((Offer) ActionOfOpponent).getBid(),
                        utilitySpace.getDomain(),
                        (AdditiveUtilitySpace) this.utilitySpace);
                this.updateConcedeDegree();
                System.out.println("the opponent propose first and we response secondly receiveMessage opponent model first");
                if (ownBidHistory.numOfBidsProposed() == 0) {
                    // receiveMessage the estimation
                    action = new Offer(agentId, (bid = this.bid_maximum_utility));
                    System.out.println("we propose secondly");
                } else {
                    // other conditions
                    round = estimateRoundLeft(true);
                    System.out.println("otherwise: round ->" + round);
                    boolean acceptFlg;
                    boolean endFlg;
                    if (round > 10) {
                        // still have some rounds left to further negotiate (the major negotiation period)
                        bid = generateBidToOffer();
                        boolean IsAccept = AcceptOpponentOffer(((Offer) ActionOfOpponent).getBid(), bid);
                        boolean IsTerminate = TerminateCurrentNegotiation(bid);
                        acceptFlg = (IsAccept && !IsTerminate)
                                || ((IsAccept && IsTerminate)
                                && (this.utilitySpace.getUtility(((Offer) ActionOfOpponent).getBid()) > this.reservationValue));
                        endFlg = (IsTerminate && !IsAccept)
                                || ((IsAccept && IsTerminate)
                                && (this.utilitySpace.getUtility(((Offer) ActionOfOpponent).getBid()) <= this.reservationValue));
                        if (!acceptFlg && !endFlg) {
                            // we expect that the negotiation is over once we select a bid from the opponent's history.
                            if (this.concedeToOpponent) {
                                bid = opponentBidHistory.getBestBidInHistory();
                                this.concedeToOpponent = false;
                                this.toughAgent = true;
                            } else {
                                this.toughAgent = false;
                            }
                        }
                    } else {
                        // this is the last chance and we concede by providing the opponent the best offer he ever proposed to us
                        // in this case, it corresponds to an opponent whose decision time is short
                        if (timeline.getTime() > 0.9985 && round < 5) {
                            bid = opponentBidHistory.getBestBidInHistory();
                            // this is specially designed to avoid that we got very low utility by
                            // searching between an acceptable range (when the domain is small)
                            if (this.utilitySpace.getUtility(bid) < 0.85) {
                                List<Bid> candidateBids = this.getBidsBetweenUtility(
                                        this.MaximumUtility - 0.15,
                                        this.MaximumUtility - 0.02);
                                // if the candidate bids do not exsit and also the deadline is approaching in next round, we concede.
                                // if (candidateBids.size() == 1 && timeline.getTime()>0.9998) {
                                // we have no chance to make a new proposal before the deadline
                                if (this.estimateRoundLeft(true) < 2) {
                                    bid = opponentBidHistory.getBestBidInHistory();
                                } else {
                                    bid = opponentBidHistory.ChooseBid(candidateBids, this.utilitySpace.getDomain());
                                }
                                // 经过以上两步bid不可能是null
                                /*if (bid == null) {
                                    bid = opponentBidHistory.getBestBidInHistory();
                                }*/
                            }
                            boolean IsAccept = AcceptOpponentOffer(((Offer) ActionOfOpponent).getBid(), bid);
                            boolean IsTerminate = TerminateCurrentNegotiation(bid);
                            acceptFlg = (IsAccept && !IsTerminate)
                                    || ((IsTerminate && IsAccept)
                                    && (this.utilitySpace.getUtility(((Offer) ActionOfOpponent).getBid()) > this.reservationValue))
                                    || (this.toughAgent == true);
                            endFlg = (IsTerminate && !IsAccept)
                                    || ((IsTerminate && IsAccept)
                                    && (this.utilitySpace.getUtility(((Offer) ActionOfOpponent).getBid()) <= this.reservationValue));
                            // in this case, it corresponds to the situation
                            // that we encounter an opponent who needs more
                            // computation to make decision each round
                        } else {// we still have some time to negotiate,
                            // and be tough by sticking with the lowest one in
                            // previous offer history.
                            // we also have to make the decisin fast to avoid
                            // reaching the deadline before the decision is made
                            // bid = ownBidHistory.GetMinBidInHistory();//reduce
                            // the computational cost
                            bid = generateBidToOffer();
                            boolean IsAccept = AcceptOpponentOffer(((Offer) ActionOfOpponent).getBid(), bid);
                            boolean IsTerminate = TerminateCurrentNegotiation(bid);
                            acceptFlg = (IsAccept && !IsTerminate)
                                    || ((IsTerminate && IsAccept)
                                    && (this.utilitySpace.getUtility(((Offer) ActionOfOpponent).getBid()) > this.reservationValue));
                            endFlg = (IsTerminate && !IsAccept)
                                    || ((IsTerminate && IsAccept)
                                    && (this.utilitySpace.getUtility(((Offer) ActionOfOpponent).getBid()) <= this.reservationValue));

                        }
                    }
                    if (acceptFlg) action = new Accept(agentId, ((ActionWithBid) ActionOfOpponent).getBid());
                    if (endFlg) action = new EndNegotiation(agentId);
                    if (!acceptFlg && !endFlg) action = new Offer(agentId, bid);
                }
            }
            this.ownBidHistory.addBid(bid, (AdditiveUtilitySpace) utilitySpace);
            this.timeLeftAfter = timeline.getCurrentTime();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exception in ChooseAction:" + e.getMessage());
            action = new EndNegotiation(agentId); // terminate if anything goes wrong.
        }
        return action;
    }


    /**
     * principle: randomization over those candidate bids to let the opponent
     * have a better model of my utility profile return the bid to be offered in the next round
     */
    private Bid generateBidToOffer() {
        double decreasingAmount_1 = 0.1; //0.05;
        double decreasingAmount_2 = 0.25;
        try {
            double minimumOfBidUtiity;
            // used when the domain is very large. make concession when the domin is large
            if (this.discountingFactor == 1 && this.maximumOfBid > 3000) {
                minimumOfBidUtiity = this.MaximumUtility - decreasingAmount_1;
                // make further concession when the deadline is approaching and the domain is large
                if (this.discountingFactor > 1 - decreasingAmount_2 && this.maximumOfBid > 10000 && timeline.getTime() >= 0.98) {
                    minimumOfBidUtiity = this.MaximumUtility - decreasingAmount_2;
                }
                this.utilitythreshold = Math.min(this.utilitythreshold, minimumOfBidUtiity);
            } else {
                // the general case
//                if (timeline.getTime() <= this.concedeToDF) {
                double minThreshold = (this.MaximumUtility * this.discountingFactor)
                        / Math.pow(this.discountingFactor, this.concedeToDF);
                this.utilitythreshold = this.MaximumUtility - (this.MaximumUtility - minThreshold)
                        * Math.pow((timeline.getTime() / this.concedeToDF), alpha1);
//                } else {
//                    this.utilitythreshold = (this.MaximumUtility * this.discountingFactor)
//                            / Math.pow(this.discountingFactor, timeline.getTime());
//                }
                minimumOfBidUtiity = this.utilitythreshold;
            }
//            if (timeline.getCurrentTime() > 0.4) {
//                minimumOfBidUtiity = this.utilitythreshold - decreasingAmount_1/2;
//            } else {
//                minimumOfBidUtiity = this.utilitythreshold - decreasingAmount_1;
//            }
            // choose from the opponent bid history first to reduce calculation time
            System.out.println("utilitythreshold:" + this.utilitythreshold + " minimumOfBidUtiity:" + minimumOfBidUtiity);
            // utilitythreshold不能为最大，要比最大小点， 这里设置为小0.1
//            this.utilitythreshold = Math.min(this.utilitythreshold, 0.9);
            Bid bestBidOfferedByOpponent = opponentBidHistory.getBestBidInHistory();
            if (utilitySpace.getUtility(bestBidOfferedByOpponent) >= this.utilitythreshold/*
                    || utilitySpace.getUtility(bestBidOfferedByOpponent) >= minimumOfBidUtiity*/) {
                return bestBidOfferedByOpponent;
            }
            List<Bid> candidateBids = this.getBidsBetweenUtility(minimumOfBidUtiity, this.MaximumUtility);
            Bid retBid = opponentBidHistory.ChooseBid(candidateBids, this.utilitySpace.getDomain());
            System.out.println("retBid.utility: " + this.utilitySpace.getUtility(retBid));
            return retBid;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.getMessage() + "exception in method BidToOffer");
            return null;
        }
    }

    /**
     * decide whether to accept the current offer or not
     */
    private boolean AcceptOpponentOffer(Bid opponentBid, Bid ownBid) {
        double currentUtility = 0d;
        double nextRoundUtility = 0d;
//        double maximumUtility = this.MaximumUtility;
//        double approximateBestBidUtility = 0d;

        try {
            currentUtility = utilitySpace.getUtilityWithDiscount(opponentBid, timeline);
            nextRoundUtility = utilitySpace.getUtility(ownBid);
            System.out.println("0.AcceptOpponentOffer");
            System.out.println("AcceptOpponentOffer this.utilitythreshold:" + this.utilitythreshold + " currentUtility: " + currentUtility + " nextRoundUtility: " + nextRoundUtility + " this.concedeToDF:" + this.concedeToDF);
//            approximateBestBidUtility = utilitySpace.getUtility(opponentBidHistory.getBestBidInHistory()) - this.utilityInterval;
//            System.out.println("currentUtility: " + currentUtility + " nextRoundUtility: " + nextRoundUtility + " approximateBestBidUtility: " + approximateBestBidUtility);

            this.concedeToOpponent = false;

            if (currentUtility >= this.utilitythreshold || currentUtility >= nextRoundUtility) {
                System.out.println("1.currentUtility >= this.utilitythreshold || currentUtility >= nextRoundUtility");
                System.out.println("Accept utilitythreshold: " + this.utilitythreshold);
                return true;
            } else {
                // if the current utility with discount is larger than the predicted maximum utility with discount then accept it.
                double predictMaximumUtility = this.MaximumUtility * this.discountingFactor;
                double currentMaximumUtility = this.utilitySpace.getUtilityWithDiscount(opponentBidHistory.getBestBidInHistory(), timeline);
                System.out.println("2,currentUtility < this.utilitythreshold && currentUtility < nextRoundUtility");
                System.out.println("Accept currentMaximumUtility: " + currentMaximumUtility + " predictMaximumUtility:" + predictMaximumUtility);
                if (currentMaximumUtility > predictMaximumUtility && timeline.getTime() > this.concedeToDF) {
                    System.out.println("2.1.currentMaximumUtility > predictMaximumUtility && timeline.getTime() > this.concedeToDF");
                    // if the current offer is approximately as good as the best one in the history, then accept it.
                    /*if (currentUtility >= approximateBestBidUtility) {
                        System.out.println("Accept approximateBestBidUtility: " + approximateBestBidUtility);
                        return true;
                    } else*/
                    if (currentUtility >= currentMaximumUtility - this.utilityInterval) {
                        System.out.println("2.1.1.currentUtility >= currentMaximumUtility - this.utilityInterval");
                        System.out.println("he offered me "
                                + currentMaximumUtility
                                + " we predict we can get at most "
                                + predictMaximumUtility
                                + "we concede now to avoid lower payoff due to conflict");
                        return true;
                    } else {
                        System.out.println("2.1.2.currentUtility < currentMaximumUtility - this.utilityInterval");
                        this.concedeToOpponent = true;
                        return false;
                    }
                    // retrieve the opponent's biding history and utilize it
                } else if (currentMaximumUtility > this.utilitythreshold * Math.pow(this.discountingFactor, timeline.getTime())) {
                    System.out.println("2.2.currentMaximumUtility > this.utilitythreshold * Math.pow(this.discountingFactor, timeline.getTime())");
                    // if the current offer is approximately as good as the best one in the history, then accept it.
                    //                    if (currentUtility >= approximateBestBidUtility) {
                    if (currentUtility >= currentMaximumUtility - this.utilityInterval) {
                        System.out.println("2.2.1.currentUtility >= currentMaximumUtility - this.utilityInterval");
                        return true;
                    } else {
                        System.out.println("2.2.2.currentUtility < currentMaximumUtility - this.utilityInterval");
                        System.out.println("test" + utilitySpace.getUtility(opponentBid) + this.utilitythreshold);
                        this.concedeToOpponent = true;
                        return false;
                    }
                } else {
                    System.out.println("2.3.else");
                    return false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }
    }

    /*
     * decide whether or not to terminate now
     */
    private boolean TerminateCurrentNegotiation(Bid ownBid) {
        double currentUtility = this.reservationValue;
        double nextRoundUtility = 0;
        double maximumUtility = this.MaximumUtility;
        this.concedeToOpponent = false;
        try {
            nextRoundUtility = this.utilitySpace.getUtility(ownBid);

            if (currentUtility >= this.utilitythreshold || currentUtility >= nextRoundUtility) {
                return true;
            } else {
                // if the current reseravation utility with discount is larger than
                // the predicted maximum utility with discount
                // then terminate the negotiation.
                double predictMaximumUtility = maximumUtility * this.discountingFactor;
                double currentMaximumUtility = this.utilitySpace.getReservationValueWithDiscount(timeline);
                if (currentMaximumUtility > predictMaximumUtility && timeline.getTime() > this.concedeToDF) {
                    return true;
                } else {
                    return false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // TODO
            return false;
        }
    }

    /**
     * estimate the number of rounds left before reaching the deadline @param
     * opponent @return
     */
    private int estimateRoundLeft(boolean opponent) {
        if (opponent == true) {
            if (Math.abs(this.timeLeftBefore - this.timeLeftAfter) > this.maximumTimeOfOpponent) {
                this.maximumTimeOfOpponent = Math.abs(this.timeLeftBefore - this.timeLeftAfter);
            }
        } else {
            if (Math.abs(this.timeLeftAfter - this.timeLeftBefore) > this.maximumTimeOfOwn) {
                this.maximumTimeOfOwn = Math.abs(this.timeLeftAfter - this.timeLeftBefore);
            }
        }
        System.out.println("maximumTimeOfOpponent: " + this.maximumTimeOfOpponent + " maximumTimeOfOwn: " + this.maximumTimeOfOwn);
        if (this.maximumTimeOfOpponent + this.maximumTimeOfOwn == 0) {
            System.out.println("divided by zero exception");
            // TODO 这里应该有逻辑，否则会出错
            // 取了绝对值后应该不会出现这种问题了，除非双方的出标时间都是0
        }
        return (int) ((this.totalTime - timeline.getCurrentTime()) / (this.maximumTimeOfOpponent + this.maximumTimeOfOwn));
    }

    /**
     * pre-processing to save the computational time each round
     * 计算最大utility（MaximumUtility）和最小utility阈值（minimumUtilityThreshold）间的bid
     */
    private void calculateBidsBetweenUtility() {
        BidIterator myBidIterator = new BidIterator(this.utilitySpace.getDomain());

        try {
            int maximumRounds = (int) ((this.MaximumUtility - this.minimumUtilityThreshold) / this.utilityInterval);
            // initalization for each arraylist storing the bids between each range
            for (int i = 0; i < maximumRounds; i++) {
                ArrayList<Bid> BidList = new ArrayList<>();
                this.bidsBetweenUtility.add(BidList);
            }
            this.bidsBetweenUtility.get(maximumRounds - 1).add(this.bid_maximum_utility);
            // note that here we may need to use some trick to reduce the computation cost (to be checked later);
            // add those bids in each range into the corresponding arraylist
            // wrn 原代码为：若maximumOfBid小鱼20000，则将所有可能的bid按照分好的区间放在相应的Bidlist中；若maximumOfBid大于20000，则随机生成20000个bid并放入相应区间。
            // 我暂时改为，无论maximumOfBid是多少，我最多只取前20000
            int limits = 0;
            // TODO 这段代码可以再优化
            while (limits < 20000 && myBidIterator.hasNext()) {
                Bid b = myBidIterator.next();
                for (int i = 0; i < maximumRounds; i++) {
                    if (utilitySpace.getUtility(b) <= (i + 1) * this.utilityInterval + this.minimumUtilityThreshold
                            && utilitySpace.getUtility(b) >= i * this.utilityInterval + this.minimumUtilityThreshold) {
                        this.bidsBetweenUtility.get(i).add(b);
                        break;
                    }
                }
                limits++;
            }
        } catch (Exception e) {
            System.out.println("Exception in calculateBidsBetweenUtility()");
            e.printStackTrace();
        }
    }

    /**
     * 生成值为随机选取的bid（目前没有用）
     *
     * @return
     */
    private Bid RandomSearchBid() throws Exception {
        HashMap<Integer, Value> values = new HashMap<>();
        List<Issue> issues = utilitySpace.getDomain().getIssues();
        // 用随机的Value生成bid
        for (Issue lIssue : issues) {
            switch (lIssue.getType()) {
                case DISCRETE:
                    IssueDiscrete lIssueDiscrete = (IssueDiscrete) lIssue;
                    int optionIndex = rand
                            .nextInt(lIssueDiscrete.getNumberOfValues());

                    values.put(lIssue.getNumber(),
                            lIssueDiscrete.getValue(optionIndex));
                    break;
                case REAL:
                    IssueReal lIssueReal = (IssueReal) lIssue;
                    int optionInd = rand.nextInt(
                            lIssueReal.getNumberOfDiscretizationSteps() - 1);
                    values.put(lIssueReal.getNumber(),
                            new ValueReal(lIssueReal.getLowerBound() + (lIssueReal
                                    .getUpperBound() - lIssueReal.getLowerBound())
                                    * (optionInd) / (lIssueReal
                                    .getNumberOfDiscretizationSteps())));
                    break;
                case INTEGER:
                    IssueInteger lIssueInteger = (IssueInteger) lIssue;
                    int optionIndex2 = lIssueInteger.getLowerBound()
                            + rand.nextInt(lIssueInteger.getUpperBound()
                            - lIssueInteger.getLowerBound());
                    values.put(lIssueInteger.getNumber(),
                            new ValueInteger(optionIndex2));
                    break;
                default:
                    throw new Exception("issue type " + lIssue.getType() + " not supported");
            }
        }
        return new Bid(utilitySpace.getDomain(), values);
    }

    /**
     * Get all the bids within a given utility range.
     * interval 0.01
     */
    private List<Bid> getBidsBetweenUtility(double lowerBound, double upperBound) {
        List<Bid> bidsInRange = new ArrayList<>();
        try {
            int range = (int) ((upperBound - this.minimumUtilityThreshold) / this.utilityInterval);
            int initial = (int) ((lowerBound - this.minimumUtilityThreshold) / this.utilityInterval);
            for (int i = initial; i < range; i++) {
                bidsInRange.addAll(this.bidsBetweenUtility.get(i));
            }
            if (bidsInRange.isEmpty()) {
                bidsInRange.add(this.bid_maximum_utility);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bidsInRange;
    }

    /**
     * determine the lowest bound of our utility threshold based on the
     * discounting factor we think that the minimum utility threshold should not
     * be related with the discounting degree.
     */
    private void chooseUtilityThreshold() {
        this.minimumUtilityThreshold = 0;// this.MaximumUtility - 0.09;
    }

    /*
     * determine concede-to-time degree based on the discounting factor.
     */

    private void chooseConcedeToDiscountingDegree() {
        double beta;// 1.3;
        // this value controls the rate at which the
        // agent concedes to the discouting factor.
        // the larger beta is, the more the agent makes concesions.
        // if (utilitySpace.getDomain().getNumberOfPossibleBids() > 100) {
        /*
         * if (this.maximumOfBid > 100) { beta = 2;//1.3; } else { beta = 1.5; }
         */
        // the vaule of beta depends on the discounting factor (trade-off
        // between concede-to-time degree and discouting factor)
        if (this.discountingFactor > 0.75) {
            beta = 1.8;
        } else if (this.discountingFactor > 0.5) {
            beta = 1.5;
        } else {
            beta = 1.2;
        }
        double alpha = Math.pow(this.discountingFactor, beta);
        this.concedeToDF = this.minconcedeToDF + (1 - this.minconcedeToDF) * alpha;
        this.concedeToDFOriginal = this.concedeToDF;
        System.out.println("concedeToDF is " + this.concedeToDF + "current time is " + timeline.getTime());
    }

    /**
     * receiveMessage the concede-to-time degree based on the predicted
     * toughness degree of the opponent
     */
    private void updateConcedeDegree() {
        double gama = 10;
        double weight = 0.1;
        double opponnetToughnessDegree = this.opponentBidHistory.getConcessionDegree();
        this.concedeToDF = Math.min(this.concedeToDFOriginal + weight * (1 - this.concedeToDFOriginal) * Math.pow(opponnetToughnessDegree, gama), 1);
    }

    @Override
    public String getDescription() {
        return "group5's agent";
    }

}
