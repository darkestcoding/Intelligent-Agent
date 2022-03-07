package group5;

import genius.core.Bid;
import genius.core.Domain;
import genius.core.issue.*;
import genius.core.utility.AdditiveUtilitySpace;

import java.util.*;

/**
 * Operations related with the opponent's bid history. The opponent's first 100
 * unique bids are remembered.
 * 
 * @author Justin
 */
public class OpponentBidHistory {

	private ArrayList<Bid> bidHistory = new ArrayList<>();
	private ArrayList<ArrayList<Integer>> opponentBidsStatisticsForReal = new ArrayList<>();
	private ArrayList<HashMap<Value, Integer>> opponentBidsStatisticsDiscrete = new ArrayList<>();
	private ArrayList<ArrayList<Integer>> opponentBidsStatisticsForInteger = new ArrayList<>();
	private int maximumBidsStored = 100;
	private HashMap<Bid, Integer> bidCounter = new HashMap<>();
	// the bid with maximum utility proposed by the opponent so far.
	private Bid bid_maximum_from_opponent;

	public void addBid(Bid bid, AdditiveUtilitySpace utilitySpace) {
		if (bidHistory.indexOf(bid) == -1) {
			bidHistory.add(bid);
		}
		try {
			// 更新opponent的最大bid
			if (bidHistory.size() == 1) {
				this.bid_maximum_from_opponent = bidHistory.get(0);
			} else {
				if (utilitySpace.getUtility(bid) > utilitySpace.getUtility(this.bid_maximum_from_opponent)) {
					this.bid_maximum_from_opponent = bid;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Bid getBestBidInHistory() {
		return this.bid_maximum_from_opponent;
	}

	/**
	 * initialization
	 */
	public void initializeDataStructures(Domain domain) {
		try {
			List<Issue> issues = domain.getIssues();
			for (Issue lIssue : issues) {
				switch (lIssue.getType()) {
					case DISCRETE:
						IssueDiscrete lIssueDiscrete = (IssueDiscrete) lIssue;
						HashMap<Value, Integer> discreteIssueValuesMap = new HashMap<Value, Integer>();
						for (int j = 0; j < lIssueDiscrete.getNumberOfValues(); j++) {
							Value v = lIssueDiscrete.getValue(j);
							discreteIssueValuesMap.put(v, 0);
						}
						opponentBidsStatisticsDiscrete.add(discreteIssueValuesMap);
						break;

					case REAL:
						IssueReal lIssueReal = (IssueReal) lIssue;
						ArrayList<Integer> numProposalsPerValue = new ArrayList<Integer>();
						int lNumOfPossibleValuesInThisIssue = lIssueReal
								.getNumberOfDiscretizationSteps();
						for (int i = 0; i < lNumOfPossibleValuesInThisIssue; i++) {
							numProposalsPerValue.add(0);
						}
						opponentBidsStatisticsForReal.add(numProposalsPerValue);
						break;

					case INTEGER:
						IssueInteger lIssueInteger = (IssueInteger) lIssue;
						ArrayList<Integer> numOfValueProposals = new ArrayList<Integer>();

						// number of possible value when issue is integer (we should
						// add 1 in order to include all values)
						int lNumOfPossibleValuesForThisIssue = lIssueInteger
								.getUpperBound()
								- lIssueInteger.getLowerBound()
								+ 1;
						for (int i = 0; i < lNumOfPossibleValuesForThisIssue; i++) {
							numOfValueProposals.add(0);
						}
						opponentBidsStatisticsForInteger.add(numOfValueProposals);
						break;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("EXCEPTION in initializeDataAtructures");
		}
	}

	/**
	 * This function updates the opponent's Model by calling the
	 * updateStatistics method
	 */
	public void updateOpponentModel(Bid bidToUpdate, Domain domain, AdditiveUtilitySpace utilitySpace) {
		this.addBid(bidToUpdate, utilitySpace);
		int counter = bidCounter.get(bidToUpdate) == null ? 1 : (bidCounter.get(bidToUpdate) + 1);
		bidCounter.put(bidToUpdate, counter);
		// 在需要计算的bid范围内更新统计数据，超过，则不计入
		if (this.bidHistory.size() <= this.maximumBidsStored) {
			this.updateStatistics(bidToUpdate, false, domain);
		}
	}

	/**
	 * This function updates the statistics of the bids that were received from the opponent.
	 */
	private void updateStatistics(Bid bidToUpdate, boolean toRemove, Domain domain) {
		try {
			// counters for each type of the issues
			int realIndex = 0;
			int discreteIndex = 0;
			int integerIndex = 0;
			for (Issue lIssue : domain.getIssues()) {
				Value v = bidToUpdate.getValue(lIssue.getNumber());
				switch (lIssue.getType()) {
					case DISCRETE:
						if (opponentBidsStatisticsDiscrete.size() > discreteIndex && opponentBidsStatisticsDiscrete.get(discreteIndex) != null) {
							int counterPerValue = opponentBidsStatisticsDiscrete.get(discreteIndex).get(v);
							opponentBidsStatisticsDiscrete.get(discreteIndex).put(v, toRemove ? Math.max(--counterPerValue, 0) : ++counterPerValue);
						} else {
							// 这种情况不太可能有
							System.out.println("opponentBidsStatisticsDiscrete is NULL");
						}
						discreteIndex++;
						break;

					case REAL:
						IssueReal lIssueReal = (IssueReal) lIssue;
						int lNumOfPossibleRealValues = lIssueReal.getNumberOfDiscretizationSteps();
						double lOneStep = (lIssueReal.getUpperBound() - lIssueReal.getLowerBound()) / lNumOfPossibleRealValues;
						double first = lIssueReal.getLowerBound();
						double last = lIssueReal.getLowerBound() + lOneStep;
						double valueReal = ((ValueReal) v).getValue();
						boolean found = false;

						for (int i = 0; !found && i < opponentBidsStatisticsForReal.get(realIndex).size(); i++) {
							if (valueReal >= first && valueReal <= last) {
								int countPerValue = opponentBidsStatisticsForReal.get(realIndex).get(i);
								opponentBidsStatisticsForReal.get(realIndex).set(i, toRemove ? Math.max(--countPerValue, 0) : ++countPerValue);
								found = true;
							}
							first = last;
							last = last + lOneStep;
						}
						// If no matching value was found, receiveMessage the last cell
						if (found == false) {
							int i = opponentBidsStatisticsForReal.get(realIndex).size() - 1;
							int countPerValue = opponentBidsStatisticsForReal.get(realIndex).get(i);
							opponentBidsStatisticsForReal.get(realIndex).set(i, toRemove ? Math.max(--countPerValue, 0) : ++countPerValue);
						}
						realIndex++;
						break;
					case INTEGER:
						IssueInteger lIssueInteger = (IssueInteger) lIssue;
						int valueInteger = ((ValueInteger) v).getValue();
						// For ex.
						int valueIndex = valueInteger - lIssueInteger.getLowerBound();
						// LowerBound index is 0, and the lower bound is 2, the value is 4, so the index of 4 would be 2 which is exactly 4-2
						int countPerValue = opponentBidsStatisticsForInteger.get(integerIndex).get(valueIndex);
						opponentBidsStatisticsForInteger.get(integerIndex).set(valueIndex, toRemove ? Math.max(--countPerValue, 0) : ++countPerValue);
						integerIndex++;
						break;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * choose a bid which is optimal for the opponent among a set of candidate bids.
	 * 找不到就随机生成一个
	 * @param candidateBids
	 * @param domain
	 * @return
	 */
	public Bid ChooseBid(List<Bid> candidateBids, Domain domain) {
		int upperSearchLimit = 200;// 100;

		// 随机挑选100bid（允许有重复）
		Random ran = new Random();
		if (candidateBids.size() >= upperSearchLimit) {
			List<Bid> bids = new ArrayList<>();
			for (int i = 0; i < upperSearchLimit; i++) {
				bids.add(candidateBids.get(ran.nextInt(candidateBids.size())));
			}
			candidateBids = bids;
		}

		// this whole block of code is to find the best bid
		List<Issue> issues = domain.getIssues();
		int maxIndex = -1;
		int maxFrequency = 0;
		int realIndex = 0;
		int discreteIndex = 0;
		int integerIndex = 0;
		try {
			for (int i = 0; i < candidateBids.size(); i++) {
				int maxValue = 0;
				realIndex = discreteIndex = integerIndex = 0;
				for (int j = 0; j < issues.size(); j++) {
					Value v = candidateBids.get(i).getValue(issues.get(j).getNumber());
					switch (issues.get(j).getType()) {
						case DISCRETE:
							if (opponentBidsStatisticsDiscrete.get(discreteIndex) != null) {
								maxValue += opponentBidsStatisticsDiscrete.get(discreteIndex).get(v);
							}
							discreteIndex++;
							break;
						case REAL:
							IssueReal lIssueReal = (IssueReal) issues.get(j);
							int lNumOfPossibleRealValues = lIssueReal.getNumberOfDiscretizationSteps();
							double lOneStep = (lIssueReal.getUpperBound() - lIssueReal.getLowerBound()) / lNumOfPossibleRealValues;
							double first = lIssueReal.getLowerBound();
							double last = lIssueReal.getLowerBound() + lOneStep;
							double valueReal = ((ValueReal) v).getValue();
							boolean found = false;
							for (int k = 0; !found && k < opponentBidsStatisticsForReal.get(realIndex).size(); k++) {
								if (valueReal >= first && valueReal <= last) {
									int counterPerValue = opponentBidsStatisticsForReal.get(realIndex).get(k);
									maxValue += counterPerValue;
									found = true;
								}
								first = last;
								last = last + lOneStep;
							}
							if (found == false) {
								int k = opponentBidsStatisticsForReal.get(realIndex).size() - 1;
								int counterPerValue = opponentBidsStatisticsForReal.get(realIndex).get(k);
								maxValue += counterPerValue;
							}
							realIndex++;
							break;

						case INTEGER:
							IssueInteger lIssueInteger = (IssueInteger) issues.get(j);
							int valueInteger = ((ValueInteger) v).getValue();
							int valueIndex = valueInteger - lIssueInteger.getLowerBound(); // For ex.
							// LowerBound index is 0, and the lower bound is 2, the value is 4, so the index of 4 would be 2 which is exactly 4-2
							int counterPerValue = opponentBidsStatisticsForInteger.get(integerIndex).get(valueIndex);
							maxValue += counterPerValue;
							integerIndex++;
							break;
					}
				}
				// choose the bid with the maximum maxValue || random exploration
				if ((maxValue > maxFrequency) || (maxValue == maxFrequency && ran.nextDouble() < 0.5)) {
					maxFrequency = maxValue;
					maxIndex = i;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Exception in choosing a bid");
//			System.out.println(e.getMessage() + "---" + discreteIndex);
		}

		// here we adopt the random exploration mechanism
		if (maxIndex == -1 || ran.nextDouble() >= 0.95) {
			return candidateBids.get(ran.nextInt(candidateBids.size()));
		} else {
			return candidateBids.get(maxIndex);
		}
	}

	/*
	 * return the best bid from the opponent's bidding history
	 * 这个方法不就是getBestBidInHistory()吗？
	 * 暂时没有地方用到，注掉
	 */
	/*public Bid chooseBestFromHistory(AdditiveUtilitySpace utilitySpace) {
		double max = -1;
		Bid maxBid = null;
		try {
			for (Bid bid : bidHistory) {
				if (max < utilitySpace.getUtility(bid)) {
					max = utilitySpace.getUtility(bid);
					maxBid = bid;
				}
			}
		} catch (Exception e) {
			System.out.println("ChooseBestfromhistory exception");
		}
		return maxBid;
	}*/

	/**
	 * one way to predict the concession degree of the opponent
	 */
	public double concedeDegree(AdditiveUtilitySpace utilitySpace) {
		/* 原逻辑
		HashMap<Bid, Integer> bidCounter = new HashMap<>();
		try {
			for (Bid bid :bidHistory){
				if (bidCounter.get(bid) == null) {
					bidCounter.put(bid, 1);
				} else {
					int counter = bidCounter.get(bid);
					bidCounter.put(bid, ++counter);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ((double) bidCounter.size() / utilitySpace.getDomain().getNumberOfPossibleBids());
		*/
		int distinctHistoryBidNum = new HashSet<>(bidHistory).size();
		return ((double) distinctHistoryBidNum / utilitySpace.getDomain().getNumberOfPossibleBids());
	}

    /**
     * 这个获取的是bidHistory的不重复bid数量
	 * @return
     */
	public int getSize() {
		/* 原逻辑
		int numOfBids = bidHistory.size();
		HashMap<Bid, Integer> bidCounter = new HashMap<Bid, Integer>();
		try {
			for (int i = 0; i < numOfBids; i++) {

				if (bidCounter.get(bidHistory.get(i)) == null) {
					bidCounter.put(bidHistory.get(i), 1);
				} else {
					int counter = bidCounter.get(bidHistory.get(i));
					counter++;
					bidCounter.put(bidHistory.get(i), counter);
				}
			}
		} catch (Exception e) {
			System.out.println("getSize exception");
		}
		return bidCounter.size();*/

		return new HashSet<>(bidHistory).size();
	}

	/**
     * Another way to predict the opponent's concession degree
	 * @return
     */
	public double getConcessionDegree() {
		int numOfBids = bidHistory.size();
		double numOfDistinctBid;
		int historyLength = 10;
		if (numOfBids > historyLength) {
			/* 原逻辑为获取最后十个bid中不重复bid的数量
			try {
				for (int j = numOfBids - historyLength; j < numOfBids; j++) {
					if (bidCounter.get(bidHistory.get(j)) == 1) {
						numOfDistinctBid++;
					}
				}
				concessionDegree = Math.pow(numOfDistinctBid / historyLength, 2);
			} catch (Exception e) {
				e.printStackTrace();
			}*/
			numOfDistinctBid = new HashSet<>(bidHistory.subList(numOfBids - historyLength, numOfBids - 1)).size();
		} else {
			numOfDistinctBid = this.getSize();
		}
		return Math.pow(numOfDistinctBid / historyLength, 2);
	}
}
