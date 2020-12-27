package group29;

import java.util.*;
import java.util.stream.Collectors;

import agents.anac.y2016.caduceus.agents.Caduceus.Opponent;
import genius.core.Agent;
import genius.core.AgentID;
import genius.core.Bid;
import genius.core.Domain;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.EndNegotiation;
import genius.core.actions.Offer;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.EvaluatorDiscrete;

/**
 * A simple example agent that makes random bids above a minimum target utility. 
 *
 * @author Tim Baarslag
 */
public class Agent29 extends AbstractNegotiationParty
{
	private static double MINIMUM_TARGET = 0.8;
	private static double Reluctance = 1;
	private Bid lastOffer;
	private static int roundCount;
	private ArrayList<Bid> allBids;
	private ArrayList<Integer> feasibleBidIndex;
	private ArrayList<Bid> feasibleBid;
	private ArrayList<Integer> commonBidsIndex;
	private  HashMap<AgentID, OpponentModel> opponentModelMap;
	private  List<AgentID> opponentIDList = new ArrayList<AgentID>();
	private  int agentToFavourIndex = 0;
	private AdditiveUtilitySpace additiveUtilitySpace;
	private ArrayList<Bid> rank;
	private static double care = 0.4;
	private int lastBid = 0;


	/**
	 * Initializes a new instance of the agent.
	 */
	@Override
	public void init(NegotiationInfo info) 
	{
		roundCount = 0;
		super.init(info);
		AbstractUtilitySpace utilitySpace = info.getUtilitySpace();
		AdditiveUtilitySpace additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;
		this.additiveUtilitySpace = additiveUtilitySpace;

		ArrayList<Bid> allBids = generateAllBids(additiveUtilitySpace);
		this.allBids = allBids;

		ArrayList<Integer> feasibleBidIndex = feasibleBidsIndex(allBids);
		this.feasibleBidIndex = feasibleBidIndex;

		ArrayList<Bid> feasibleBid = getFeasibleBids(allBids);
		this.feasibleBid = feasibleBid;
		ArrayList<Bid> rank = bidsRank(feasibleBid);
		this.rank = rank;

		this.opponentModelMap = new HashMap<AgentID, OpponentModel>();


		/***********************   print all settings of agent	 ***********************/
//		System.out.println("Feasible Bid Amount:" + feasibleBidIndex.size());
//
		List<Issue> issues = utilitySpace.getDomain().getIssues();
		for (Issue issue : issues) {
			int issueNumber = issue.getNumber();
			System.out.println(">> " + issue.getName() + issueNumber + " weight: " + additiveUtilitySpace.getWeight(issueNumber));

			// Assuming that issues are discrete only
			IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
			EvaluatorDiscrete evaluatorDiscrete = (EvaluatorDiscrete) additiveUtilitySpace.getEvaluator(issueNumber);

			for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
				System.out.println(valueDiscrete.getValue());
				System.out.println("Evaluation(getValue): " + evaluatorDiscrete.getValue(valueDiscrete));
				try {
					System.out.println("Evaluation(getEvaluation): " + evaluatorDiscrete.getEvaluation(valueDiscrete));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * return all the possible bid in the utility space
	 * @param additiveUtilitySpace
	 * @return
	 */
	private ArrayList<Bid> generateAllBids(AdditiveUtilitySpace additiveUtilitySpace)
	{
		ArrayList<Bid> all_bids = new ArrayList<Bid>();
		ArrayList<ArrayList<ValueDiscrete>> option_lists = new ArrayList<ArrayList<ValueDiscrete>>();
		List<Issue> issues = additiveUtilitySpace.getDomain().getIssues();
		List<Integer> issue_nums = new ArrayList<Integer>();

		for (Issue issue : issues)
		{
			issue_nums.add(issue.getNumber());
			IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
			ArrayList<ValueDiscrete> value_list = new ArrayList<ValueDiscrete>();
			for (ValueDiscrete valueDiscrete : issueDiscrete.getValues())
			{
				value_list.add(valueDiscrete);
			}
			option_lists.add(value_list);
		}
		List<List<ValueDiscrete>> cartesian_combinations = cartesianCombinations(option_lists);

		Domain domain = utilitySpace.getDomain();
		for (List<ValueDiscrete> options : cartesian_combinations)
		{
			HashMap<Integer, Value> issue_num_option = new HashMap<Integer, Value>();
			int index = 1;
			for(ValueDiscrete p : options)
			{
				issue_num_option.put(index, p);
				index += 1;
			}
			Bid bid = new Bid(domain, issue_num_option);
			all_bids.add(bid);
		}
		return all_bids;
	}

	/**
	 * Generating feasible bids set that is above the reserve value of my agent
	 * @param bids all bids you want to find that is higher than the expectation
	 * @return Feasible Bid
	 */
	private ArrayList<Integer> feasibleBidsIndex(ArrayList<Bid> bids)
	{
		ArrayList<Integer> indexes = new ArrayList<Integer>();
		ArrayList<Bid> feasible_bids = new ArrayList<Bid>();
		int index = 0;
		for(Bid bid : bids)
		{
			double utility = utilitySpace.getUtility(bid);
			if(utility >= MINIMUM_TARGET)
			{
				feasible_bids.add(bid);
				indexes.add(index);
			}
			index += 1;
		}
		return indexes;
	}


	/**
	 * get bid that not lower than our minimum target
	 * @param bids
	 * @return
	 */
	private ArrayList<Bid> getFeasibleBids(ArrayList<Bid> bids)
	{

		ArrayList<Bid> feasible_bids = new ArrayList<Bid>();
		for(Bid bid : bids)
		{
			double utility = utilitySpace.getUtility(bid);
			if(utility >= MINIMUM_TARGET)
			{
				feasible_bids.add(bid);
			}
		}
		return feasible_bids;
	}

	/**
	 * Generate Cartesian product of List, for generating all the possible bids
	 * @param <T>
	 * @param lists
	 * @return
	 */
	public static <T> List<List<T>> cartesianCombinations(ArrayList<ArrayList<T>> lists) {
		List<List<T>> currentCombinations = Arrays.asList(Arrays.asList());
		for (ArrayList<T> list : lists) {
			currentCombinations = appendElements(currentCombinations, list);
		}
		return currentCombinations;
	}

	public static <T> List<List<T>> appendElements(List<List<T>> combinations, List<T> extraElements) {
		return combinations.stream().flatMap(oldCombination
				-> extraElements.stream().map(extra -> {
			List<T> combinationWithExtra = new ArrayList<>(oldCombination);
			combinationWithExtra.add(extra);
			return combinationWithExtra;
		}))
				.collect(Collectors.toList());
	}


	/**
	 * select N best element in array according to the opponent model we have, return index of N best
	 * @param bids
	 * @param n
	 */
	private ArrayList<Integer> opponentBestNBidsIndex(ArrayList<Bid> bids, int n, OpponentModel opponentModel) {
		//create sort able array with index and value pair
		IndexValuePair[] pairs = new IndexValuePair[bids.size()];
		for (int i = 0; i < bids.size(); i++) {
			pairs[i] = new IndexValuePair(i, bids.get(i));
		}

		//sort
		Arrays.sort(pairs, new Comparator<IndexValuePair>() {
			public int compare(IndexValuePair o1, IndexValuePair o2) {
				return Float.compare(opponentModel.getUtility(o1.bid), opponentModel.getUtility(o2.bid));
			}
		});

		//extract the indices
		ArrayList<Integer> result = new ArrayList<Integer>();
		for (int i = 0; i < n; i++) {
			result.add(pairs[i].index);
		}
		return result;
	}


	/**
	 * rank all the bid with respect to agent utility space
	 * @param bids
	 * @return
	 */
	private ArrayList<Bid> bidsRank(ArrayList<Bid> bids) {
		//create sort able array with index and value pair
		IndexValuePair[] pairs = new IndexValuePair[bids.size()];
		for (int i = 0; i < bids.size(); i++) {
			pairs[i] = new IndexValuePair(i, bids.get(i));
		}

		//sort
		Arrays.sort(pairs, new Comparator<IndexValuePair>() {
			public int compare(IndexValuePair o1, IndexValuePair o2) {
				return Double.compare(utilitySpace.getUtility(o1.bid), utilitySpace.getUtility(o2.bid));
			}
		});

		//extract the indices
		ArrayList<Bid> result = new ArrayList<Bid>();
		for (int i = 0; i < bids.size(); i++) {
			result.add(feasibleBid.get(pairs[i].index));
		}
		Collections.reverse(result);
		return result;
	}


	public class IndexValuePair {
		private int index;
		private Bid bid;

		public IndexValuePair(int index, Bid bid) {
			this.index = index;
			this.bid = bid;
		}
	}

	public static ArrayList<Integer> intersection(ArrayList<Integer> l1, ArrayList<Integer> l2)
	{
		HashSet<Integer> set = new HashSet<>();
		set.addAll(l1);
		set.retainAll(l2);

		ArrayList<Integer> result = new ArrayList<>(set);
		return result;
	}

	/**
	 * return the bid index in the bid list which has the largest utility
	 * @param bidsIndex
	 * @return
	 */
	private int bestBidIndex(ArrayList<Integer> bidsIndex)
	{
		Iterator<Integer> it = bidsIndex.iterator();
		double maxUtility = 0.0;
		int maxBidIndex = bidsIndex.get(0);
		while(it.hasNext())
		{
			int bidIndex = it.next();
			double utility = utilitySpace.getUtility(allBids.get(bidIndex));
			if(utility > maxUtility)
			{
				maxBidIndex = bidIndex;
				maxUtility = utility;
			}
		}
		return maxBidIndex;
	}

	/**************       Basic Negotiation Functions of Agent       **************/
	/**
	 * Makes a random offer above the minimum utility target
	 * Accepts everything above the reservation value at the very end of the negotiation; or breaks off otherwise. 
	 */
	@Override
	public Action chooseAction(List<Class<? extends Action>> possibleActions) 
	{
		// Check for acceptance if we have received an offer
		if (lastOffer != null)
				if (getUtility(lastOffer) >= MINIMUM_TARGET)
					return new Accept(getPartyId(), lastOffer);
				else if (timeline.getTime() >= 0.99)
					return new EndNegotiation(getPartyId());

		//First 10 round: just make offer best for us
		if(roundCount < 10)
		{
			Bid bid = rank.get(lastBid);
			roundCount += 1;
			lastBid += 1;
			return new Offer(getPartyId(), bid);
		}

		//Every 10 round recalculate all properties
		if(roundCount != 0 && roundCount%10 == 0)
		{
			//Update feasible list and rank(ordered feasible list), because we got new AV
			feasibleBidIndex = feasibleBidsIndex(allBids);
			feasibleBid = getFeasibleBids(allBids);
			rank = bidsRank(feasibleBid);

			//Initialize the lists for store common bids and opponent best bids
			ArrayList<Integer> commonBidsIndex = feasibleBidIndex;
			ArrayList<Integer> bestNBidsIndex;

			//increase care for opponent and decrease the reluctance parameter for my agent
			care = care*1.04;
			Reluctance = Reluctance * 0.990;
//			System.out.println("feasible bids amount:" + feasibleBidIndex.size());

			//iterating all opponent, get their N best bids and store at a list
			ArrayList<ArrayList<Integer>> opponentBestNBidsList = new ArrayList<ArrayList<Integer>>();
			Iterator it = opponentModelMap.entrySet().iterator();
			while(it.hasNext())
			{
				Map.Entry<AgentID, OpponentModel> opponent_id_map = (Map.Entry<AgentID, OpponentModel>) it.next();
				OpponentModel opponentModel = opponent_id_map.getValue();
				opponentModel.update();
				bestNBidsIndex = opponentBestNBidsIndex(allBids, 200, opponentModel);
				opponentBestNBidsList.add(bestNBidsIndex);
				System.out.println("opponent name :" + opponent_id_map.getKey() + "\n" + opponentModel.getIssueWeightStr());
			}
			System.out.println(opponentModelMap.get(opponentIDList.get(agentToFavourIndex)).getOptionValueStr());


			//intersection my feasible bids with all bid lists from opponents, get the common bid list
			Iterator<ArrayList<Integer>> bidIt = opponentBestNBidsList.iterator();
			while(bidIt.hasNext())
			{
				commonBidsIndex = intersection(commonBidsIndex, bidIt.next());
			}

			this.commonBidsIndex = commonBidsIndex;
			System.out.println("round " + roundCount + " common bids amount: " + commonBidsIndex.size());


			//Find the best bid in common bid list and re-calculate the AV
			int bestIndex = bestBidIndex(feasibleBidIndex);
			if(commonBidsIndex.size() != 0)
			{
				bestIndex = bestBidIndex(commonBidsIndex);
			}

			System.out.println("round " + roundCount + " AV: " + MINIMUM_TARGET);
			System.out.println("round " + roundCount + " Care: " + care);

			MINIMUM_TARGET = utilitySpace.getUtility(allBids.get(bestIndex)) * Reluctance;

		}
		roundCount += 1;

		//Making an offer to opponent
		AgentID agentToFavour = opponentIDList.get(agentToFavourIndex);

		// From high to low in rank, find the bid iterative that satisfy AV and Care
		// if the bid is not accepted, move to the next bid in the rank
		for(int i = lastBid; i < rank.size()-1; i++)
		{
			if((utilitySpace.getUtility(rank.get(i)) >= MINIMUM_TARGET) && (opponentModelMap.get(agentToFavour).getUtility(rank.get(i)) >= care))
			{
//				System.out.println(opponentModelMap.get(agentToFavour).getUtility(rank.get(i)));
//				System.out.println(opponentModelMap.get(agentToFavour).getOptionValueStr());
				lastBid = i+1;
				if(agentToFavourIndex == opponentIDList.size()-1)
				{
					agentToFavourIndex = 0;
				}else{
					agentToFavourIndex += 1;
				}
				return new Offer(getPartyId(), rank.get(i));
			}
		}

		lastBid = 0;
		if(agentToFavourIndex == opponentIDList.size()-1)
		{
			agentToFavourIndex = 0;
		}else{
			agentToFavourIndex += 1;
		}

		return new Offer(getPartyId(), rank.get(0));

	}

	/**
	 * Remembers the offers received by the opponent.
	 */
	@Override
	public void receiveMessage(AgentID sender, Action action)
	{
		if (action instanceof Offer) 
		{
			lastOffer = ((Offer) action).getBid();
			if(opponentModelMap.containsKey(sender))
			{
				OpponentModel opponent = opponentModelMap.get(sender);
				opponent.addBid(lastOffer);
				opponentModelMap.replace(sender, opponent);
			}else{
				BidCounter bc = new BidCounter(additiveUtilitySpace);
				bc.init();
				OpponentModel opponentModel = new OpponentModel(additiveUtilitySpace, bc);
				opponentModel.addBid(lastOffer);
				opponentModelMap.put(sender, opponentModel);
				opponentIDList.add(sender);
			}

//				String bidCountStr = bid_counter.getBidCountStr();
//				System.out.println(bidCountStr);
//				String opponentWeight = opponentModel.getIssueWeightStr();
//				String opponentOptionValue = opponentModel.getOptionValueStr();
//				System.out.println(opponentWeight);
//				System.out.println(opponentOptionValue);
		}

	}


	@Override
	public String getDescription() 
	{
		return "ZC test 1";
	}

	/**
	 * This stub can be expanded to deal with preference uncertainty in a more sophisticated way than the default behavior.
	 */
	@Override
	public AbstractUtilitySpace estimateUtilitySpace() 
	{
		return super.estimateUtilitySpace();
	}

}
