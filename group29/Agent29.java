package group29;

import genius.core.AgentID;
import genius.core.Bid;
import genius.core.Domain;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.EndNegotiation;
import genius.core.actions.Offer;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.uncertainty.BidRanking;
import genius.core.uncertainty.ExperimentalUserModel;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.UncertainAdditiveUtilitySpace;
import linear_programming_test_model.MyAdditiveUtilitySpaceFactory;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.List;

public class Agent29 extends AbstractNegotiationParty {
    private AdditiveUtilitySpace additiveUtilitySpace;
    private OpponentModel opponentModel;
    private Bid lastOffer;
    private JonnyBlack jb;
    private AdditiveUtilitySpace realUtilitySpace;


    @Override
    public void init(NegotiationInfo info) {
        super.init(info);
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
        if (hasPreferenceUncertainty()) {
            BidRanking bidRanking = userModel.getBidRanking();
            Domain domain = bidRanking.getMaximalBid().getDomain();
            linear_programming_test_model.MyAdditiveUtilitySpaceFactory myFactory = new MyAdditiveUtilitySpaceFactory(domain);
            myFactory.estimateUsingBidRanks(bidRanking);
            AdditiveUtilitySpace additiveUtilitySpace = myFactory.getUtilitySpace();
            this.additiveUtilitySpace = additiveUtilitySpace;

            BidCounter bidCounter = new BidCounter(additiveUtilitySpace);
            bidCounter.init();
            OpponentModel opponentModel = new OpponentModel(additiveUtilitySpace, bidCounter);
            this.opponentModel = opponentModel;

            JonnyBlack jb = new JonnyBlack(additiveUtilitySpace, opponentModel);
            this.jb = jb;

            ExperimentalUserModel experimentalUserModel = (ExperimentalUserModel) userModel;
            UncertainAdditiveUtilitySpace realUtilitySpace = experimentalUserModel.getRealUtilitySpace();
            this.realUtilitySpace = realUtilitySpace;
        }
    }

    @Override
    public Action chooseAction(List<Class<? extends Action>> possibleActions) {
        // Check for acceptance if we have received an offer
        if (lastOffer != null)
            if (additiveUtilitySpace.getUtility(lastOffer) >= jb.getAgreementValue()) {
                return new Accept(this.getPartyId(), lastOffer);
            }
            else if (timeline.getTime() >= 0.99)
                return new EndNegotiation(getPartyId());

        Bid OfferingBid = jb.makeAnOffer();
        System.out.println("Predicted and Real Bid Utility for User:" + additiveUtilitySpace.getUtility(OfferingBid) + " " + realUtilitySpace.getUtility(OfferingBid));
        System.out.println("Predicted Utility for Opponent:" + jb.getOpponentUtility(OfferingBid));
        return new Offer(this.getPartyId(), OfferingBid);
    }

    @Override
    public String getDescription() {
        return "Code Simplified Agent";
    }

    /**
     * Remembers the offers received by the opponent.
     */
    @Override
    public void receiveMessage(AgentID sender, Action action)
    {
        if (action instanceof Offer)
        {
            Bid lastOffer = ((Offer) action).getBid();
            this.lastOffer = lastOffer;
            opponentModel.addBid(lastOffer);
            jb.updateOpponentModel(opponentModel);
        }

    }
}
