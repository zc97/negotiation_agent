# negotiation_agent

- 把test agent的code整合到了agent29.
- 建立了新的 MyAdditiveUtilitySpaceFactory class 继承 AdditiveUtilitySpaceFactory，建立instance of MyAdditiveUtilitySpaceFactory，调用estimateUsingBidRanks(BidRanking),然后可以调用getUtilitySpace()得到预测的UtilitySpace.
- 测试了linear regression方法预测到user preference，效果还算不错。@Override AbstractNegotiationParty class 中的 estimateUtilitySpace()，用来得到log中正确的Perceived Utility.
- Working On：写一个function可以的到每一轮出价预测值和真实值的CSV file, 用python画图。

