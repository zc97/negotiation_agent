package linear_programming_test_model;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;

import genius.core.AgentID;
import genius.core.Bid;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.uncertainty.BidRanking;

import genius.core.uncertainty.OutcomeComparison;
import gurobi.*;

/**
 * ExampleAgent returns the bid that maximizes its own utility for half of the negotiation session.
 * In the second half, it offers a random bid. It only accepts the bid on the table in this phase,
 * if the utility of the bid is higher than Example Agent's last bid.
 */
public class TestAgent extends AbstractNegotiationParty {
    private final String description = "Example Agent";

    private Bid lastReceivedOffer; // offer on the table
    private Bid myLastOffer;

    @Override
    public void init(NegotiationInfo info) {
        super.init(info);
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
        if (hasPreferenceUncertainty()) {
            System.out.println("Preference uncertainty is enabled.");
            BidRanking bidRanking = userModel.getBidRanking();
            System.out.println("The agent ID is:"+info.getAgentID());
            System.out.println("Total number of possible bids:" +userModel.getDomain().getNumberOfPossibleBids());
            System.out.println("The number of bids in the ranking is:" + bidRanking.getSize());
            System.out.println("The lowest bid is:"+bidRanking.getMinimalBid());
            System.out.println("The highest bid is:"+bidRanking.getMaximalBid());
            System.out.println("The elicitation costs are:"+user.getElicitationCost());
            List<Bid> bidList = bidRanking.getBidOrder();
            System.out.println("The 5th bid in the ranking is:"+bidList.get(4));

//            List<Issue> issues = bidRanking.getBidIssues();
//            for(Issue issue:issues){
//                System.out.println(">>> Issue Name: " + issue.getName() + " Number: " + issue.getNumber());
//                IssueDiscrete discreteIssue = (IssueDiscrete) issue;
//                List<ValueDiscrete> discreteValues = discreteIssue.getValues();
//                for(ValueDiscrete value:discreteValues){
//                    System.out.println("> Value Name: " + value.getValue() + " Number: " + ((IssueDiscrete) issue).getValueIndex(value));
//                }
//            }

            //Estimate Using Bid Ranking
            estimateUsingBidRanking(bidRanking);
        }
    }

    /**
     * When this function is called, it is expected that the Party chooses one of the actions from the possible
     * action list and returns an instance of the chosen action.
     *
     * @param list
     * @return
     */
    @Override
    public Action chooseAction(List<Class<? extends Action>> list) {
        // According to Stacked Alternating Offers Protocol list includes
        // Accept, Offer and EndNegotiation actions only.
        double time = getTimeLine().getTime(); // Gets the time, running from t = 0 (start) to t = 1 (deadline).
        // The time is normalized, so agents need not be
        // concerned with the actual internal clock.


        // First half of the negotiation offering the max utility (the best agreement possible) for Example Agent
        if (time < 0.5) {
            return new Offer(this.getPartyId(), this.getMaxUtilityBid());
        } else {

            // Accepts the bid on the table in this phase,
            // if the utility of the bid is higher than Example Agent's last bid.
            if (lastReceivedOffer != null
                    && myLastOffer != null
                    && this.utilitySpace.getUtility(lastReceivedOffer) > this.utilitySpace.getUtility(myLastOffer)) {

                return new Accept(this.getPartyId(), lastReceivedOffer);
            } else {
                // Offering a random bid
                myLastOffer = generateRandomBid();
                return new Offer(this.getPartyId(), myLastOffer);
            }
        }
    }


    public void estimateUsingBidRanking(BidRanking r){
        try {

            // Create empty environment, set options, and start
            GRBEnv env = new GRBEnv(true);
            env.set("logFile", "myAgent.log");
            env.start();

            // Create empty model
            GRBModel model = new GRBModel(env);
            model.set(GRB.IntParam.NonConvex, 2);
            //create a data struct using hash map with pattern likes:

            //        -> value1 -> variable 1,1
            // issue1
            //        -> value2 -> variable 1,2
            //
            //        -> value1 -> variable 2,1
            // issue2 -> value2 -> variable 2,2
            //        -> value2 -> variable 2,3

            List<Issue> issues = r.getBidIssues();
            HashMap<IssueDiscrete, HashMap<ValueDiscrete, String>> issueValueTable = new HashMap<>();
            for (Issue issue : issues) {
                //get the index of issues
                Integer issueNum = issue.getNumber();
                IssueDiscrete discreteIssue = (IssueDiscrete) issue;
                List<ValueDiscrete> discreteValues = discreteIssue.getValues();
                HashMap<ValueDiscrete, String> valueVarTable = new HashMap<>();
                String weightName = "w" + issueNum.toString();

                //Adding weight variables for each issue, name: w1, w2, w3 ...
                model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, weightName);
                model.update();
                System.out.println(weightName + " " + issue.getName());
                //iterate every value and put in to valueVarTable, name: u11, u12, u21, u22 ...
                for (ValueDiscrete value : discreteValues){
                    //get the index of values, adding 1 because we want the index to start at 1 not 0
                    Integer valueNum = ((IssueDiscrete) issue).getValueIndex(value) + 1;
                    String varName = "u" + issueNum.toString() + valueNum.toString();
                    System.out.println(varName + " " + value.getValue());
                    model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, varName);
                    model.update();
                    valueVarTable.put(value, varName);
                }
                issueValueTable.put(discreteIssue, valueVarTable);
            }

            //check all variables by printing
//            GRBVar[] vars = model.getVars();
//            System.out.println("**************** All Variables ****************");
//            for(GRBVar var:vars){
//                System.out.println(var.get(GRB.StringAttr.VarName));
//            }

            //initialize the e which stands for epsilon
            GRBVar e = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, "epsilon");

            // Set objective: epsilon e
            System.out.println("**************** Creating Objective ****************");
            GRBLinExpr expr = new GRBLinExpr();
            expr.addTerm(1.0, e);
            model.setObjective(expr, GRB.MAXIMIZE);


            //Set constraints with respect to the pairwise comparisons in bid ranking
            //example:
            //if we got two pairwise comparison bid2 >= bid1:
            //bid2 is offering value1 for issue1, value3 for issue2
            //bid1 is offering value2 for issue1, value2 for issue2
            //a constraint can be constructed accordingly:
            //w1*u11 + w2*u23 >= w1*u12 + w2*u22
            //For the purpose of optimization, e is added to get maximize the margin between the utility bid1 and bid2:
            //w1*u11 + w2*u23 >= w1*u12 + w2*u22 + e

            //get all partial ordered bid comparisons
            //bid1 > bid2 returns -1
            //bid1 < bid2 returns  1
            System.out.println("**************** Adding Constraints ****************");
            List<OutcomeComparison> comparisonsList = r.getPairwiseComparisons();


            //After checking the comparison result relationship  (-1,1,>=,<=), found out the comparison result is the other way around!
//            OutcomeComparison c = comparisonsList.get(10);
//
//            System.out.println(comparisonsList.size());
//            Bid b1 = c.getBid1();
//            Bid b2 = c.getBid2();
//            int cr = c.getComparisonResult();
//            System.out.println("**************** Result ****************");
//
//            System.out.println("Result: " + cr);
//
//            System.out.println("**************** Bid 1 ****************");
//
//            for (Issue issue : issues) {
//                IssueDiscrete discreteIssue = (IssueDiscrete) issue;
//                System.out.print(discreteIssue.getName() + ",");
//                System.out.print(b1.getValue(discreteIssue).toString() + ",");
//            }
//
//            System.out.println("**************** Bid 2 ****************");
//            for (Issue issue : issues) {
//                IssueDiscrete discreteIssue = (IssueDiscrete) issue;
//                System.out.print(discreteIssue.getName() + ",");
//                System.out.print(b2.getValue(discreteIssue).toString() + ",");
//            }

            Integer index = 0;
            for(OutcomeComparison comparison:comparisonsList){

                String comparisonName = "c" + index.toString();
                index ++;
                Bid bid1 = comparison.getBid1();
                Bid bid2 = comparison.getBid2();
                int result = comparison.getComparisonResult();
                GRBQuadExpr expr1 = new GRBQuadExpr();
                GRBQuadExpr expr2 = new GRBQuadExpr();
                for (Issue issue : issues){
                    Integer issueNum = issue.getNumber();
                    IssueDiscrete discreteIssue = (IssueDiscrete) issue;
                    Value value1 = bid1.getValue(discreteIssue);
                    Value value2 = bid2.getValue(discreteIssue);
                    Integer valueNum1 = discreteIssue.getValueIndex((ValueDiscrete) value1) + 1;
                    Integer valueNum2 = discreteIssue.getValueIndex((ValueDiscrete) value2) + 1;
                    String varName1 = "u" + issueNum.toString() + valueNum1.toString();
                    String varName2 = "u" + issueNum.toString() + valueNum2.toString();
                    String issueName = "w" + issueNum.toString();
                    expr1.addTerm(1.0, model.getVarByName(issueName), model.getVarByName(varName1));
                    expr2.addTerm(1.0, model.getVarByName(issueName), model.getVarByName(varName2));
                }
                char comparisonPara;
                if(result == -1){
                    comparisonPara = GRB.LESS_EQUAL;
                    expr1.addTerm(1.0, e);
                }else{
                    comparisonPara = GRB.GREATER_EQUAL;
                    expr2.addTerm(1.0, e);
                }
                model.addQConstr(expr1, comparisonPara, expr2, comparisonName);
            }

            //adding constraints that make sure the summation of values from the same issue is exact 1
            String weightSumTo1name = "weightSumTo1";
            GRBLinExpr exprWeightSumTo1 = new GRBLinExpr();
            for (Issue issue : issues) {
                String sumTo1name = issue.getName() + "sumTo1";
                IssueDiscrete discreteIssue = (IssueDiscrete) issue;
                Integer issueNum = issue.getNumber();
                String weightName = "w" + issueNum.toString();
                exprWeightSumTo1.addTerm(1.0, model.getVarByName(weightName));

                List<ValueDiscrete> discreteValues = discreteIssue.getValues();
                GRBLinExpr exprSumTo1 = new GRBLinExpr();
                for (ValueDiscrete value : discreteValues){
                    Integer valueNum = ((IssueDiscrete) issue).getValueIndex(value) + 1;
                    String varName = "u" + issueNum.toString() + valueNum.toString();
                    exprSumTo1.addTerm(1.0, model.getVarByName(varName));
                }
                model.addConstr(exprSumTo1, GRB.EQUAL, 1.0, sumTo1name);
            }
            model.addConstr(exprWeightSumTo1, GRB.EQUAL, 1.0,weightSumTo1name);
            model.addConstr(e, GRB.GREATER_EQUAL, 0, "epsilon");

            // Optimize model
            model.optimize();

            //System.out.print variables
            for (Issue issue : issues) {
                Integer issueNum = issue.getNumber();
                IssueDiscrete discreteIssue = (IssueDiscrete) issue;
                String issueName = "w" + issue.getNumber();
                System.out.println(issueName + " " + model.getVarByName(issueName).get(GRB.DoubleAttr.X));
                List<ValueDiscrete> discreteValues = discreteIssue.getValues();
                for (ValueDiscrete value : discreteValues){
                    Integer valueNum = ((IssueDiscrete) issue).getValueIndex(value) + 1;
                    String varName = "u" + issueNum.toString() + valueNum.toString();
                    System.out.println(varName + " " + model.getVarByName(varName).get(GRB.DoubleAttr.X));
                }
            }
            System.out.println("Obj e : " + model.get(GRB.DoubleAttr.ObjVal));

            // Dispose of model and environment
            model.dispose();
            env.dispose();

        } catch (GRBException e) {
            System.out.println("Error code: " + e.getErrorCode() + ". " +
                    e.getMessage());
        }
    }

    /**
     * This method is called to inform the party that another NegotiationParty chose an Action.
     * @param sender
     * @param act
     */
    @Override
    public void receiveMessage(AgentID sender, Action act) {
        super.receiveMessage(sender, act);

        if (act instanceof Offer) { // sender is making an offer
            Offer offer = (Offer) act;

            // storing last received offer
            lastReceivedOffer = offer.getBid();
        }
    }

    /**
     * A human-readable description for this party.
     * @return
     */
    @Override
    public String getDescription() {
        return description;
    }

    private Bid getMaxUtilityBid() {
        try {
            return this.utilitySpace.getMaxUtilityBid();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}