package linear_programming_test_model;

import genius.core.Bid;
import genius.core.Domain;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
import genius.core.uncertainty.AdditiveUtilitySpaceFactory;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.uncertainty.BidRanking;
import genius.core.uncertainty.OutcomeComparison;
import gurobi.*;

import java.util.HashMap;
import java.util.List;


public class MyAdditiveUtilitySpaceFactory extends AdditiveUtilitySpaceFactory  {
    /**
     * Generates an simple Utility Space on the domain, with equal weights and zero values.
     * Everything is zero-filled to already have all keys contained in the utility maps.
     *
     * @param d
     */
    public MyAdditiveUtilitySpaceFactory(Domain d) {
        super(d);
    }

    @Override
    public void estimateUsingBidRanks(BidRanking r) {
        try{
            // Create empty environment, set options, and start
            GRBEnv env = new GRBEnv(true);
            env.set("logFile", "myAgent.log");
            env.start();

            // Create empty model
            GRBModel model = new GRBModel(env);
            model.set(GRB.IntParam.NonConvex, 2);

            /*create a data struct using hash map with pattern likes:

                    -> value1 -> variable 1,1
             issue1
                    -> value2 -> variable 1,2

                    -> value1 -> variable 2,1
             issue2 -> value2 -> variable 2,2
                    -> value2 -> variable 2,3

            */

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


            /*
            Set constraints with respect to the pairwise comparisons in bid ranking
            example:
            if we got two pairwise comparison bid2 >= bid1:
            bid2 is offering value1 for issue1, value3 for issue2
            bid1 is offering value2 for issue1, value2 for issue2
            a constraint can be constructed accordingly:
            w1*u11 + w2*u23 >= w1*u12 + w2*u22
            For the purpose of optimization, e is added to get maximize the margin between the utility bid1 and bid2:
            w1*u11 + w2*u23 >= w1*u12 + w2*u22 + e
            */


            System.out.println("**************** Adding Constraints ****************");

            /*
            get all partial ordered bid comparisons
            bid1 > bid2 returns -1
            bid1 < bid2 returns  1
            */
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

            //adding constraints that make sure the summation of weight of all the issues is exact 1
            // w1 + w2 + ... + wn = 1
            String weightSumTo1name = "weightSumTo1";
            GRBLinExpr exprWeightSumTo1 = new GRBLinExpr();
            for (Issue issue : issues) {
                IssueDiscrete discreteIssue = (IssueDiscrete) issue;
                Integer issueNum = issue.getNumber();
                String weightName = "w" + issueNum.toString();
                exprWeightSumTo1.addTerm(1.0, model.getVarByName(weightName));

                //adding constraints that make sure the summation of values from the same issue is exact 1
                // u11 + u12 + u13 + u14 = 1
                // u21 + u22 + u23 + u24 = 1
                // ..
//                String sumTo1name = issue.getName() + "sumTo1";
//                List<ValueDiscrete> discreteValues = discreteIssue.getValues();
//                GRBLinExpr exprSumTo1 = new GRBLinExpr();
//                for (ValueDiscrete value : discreteValues){
//                    Integer valueNum = ((IssueDiscrete) issue).getValueIndex(value) + 1;
//                    String varName = "u" + issueNum.toString() + valueNum.toString();
//                    exprSumTo1.addTerm(1.0, model.getVarByName(varName));
//                }
//                model.addConstr(exprSumTo1, GRB.EQUAL, 1.0, sumTo1name);

                //set constraints that make sure the lowest bound of each u
//                List<ValueDiscrete> discreteValues = discreteIssue.getValues();
//                GRBLinExpr lowestBound = new GRBLinExpr();
//                for (ValueDiscrete value : discreteValues){
//                    Integer valueNum = ((IssueDiscrete) issue).getValueIndex(value) + 1;
//                    String varName = "u" + issueNum.toString() + valueNum.toString();
//                    lowestBound.addTerm(1.0, model.getVarByName(varName));
//                    String lowestBoundName = "lowestBound" + issueNum.toString() + valueNum.toString();
//                    model.addConstr(lowestBound, GRB.GREATER_EQUAL, 0.1, lowestBoundName);
//                }



            }

            model.addConstr(exprWeightSumTo1, GRB.EQUAL, 1.0,weightSumTo1name);
            model.addConstr(e, GRB.GREATER_EQUAL, 0.0, "epsilon");


            Bid bidLow = r.getMinimalBid();
            Bid bidMax = r.getMinimalBid();

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


            //set to the result to user utility model
            for (Issue issue : issues)
            {
                Integer issueNum = issue.getNumber();
                IssueDiscrete discreteIssue = (IssueDiscrete) issue;
                List<ValueDiscrete> discreteValues = discreteIssue.getValues();
                String weightName = "w" + issueNum.toString();

                for (ValueDiscrete value : discreteValues){
                    Integer valueNum = ((IssueDiscrete) issue).getValueIndex(value) + 1;
                    String varName = "u" + issueNum.toString() + valueNum.toString();
                    setUtility(issue, value, model.getVarByName(varName).get(GRB.DoubleAttr.X));
                }
                setWeight(issue, model.getVarByName(weightName).get(GRB.DoubleAttr.X));
            }

            // Dispose of model and environment
            model.dispose();
            env.dispose();

        } catch (GRBException e) {
            System.out.println("Error code: " + e.getErrorCode() + ". " +
                    e.getMessage());
        }
    }
}
