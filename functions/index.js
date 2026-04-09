const admin = require("firebase-admin");
const { onValueCreated } = require("firebase-functions/v2/database");

admin.initializeApp();

exports.notifyOnNewCampaign = onValueCreated(
  {
    ref: "/campaigns/{campaignId}",
    region: "asia-southeast1",
  },
  async (event) => {
    const campaign = event.data.val();
    if (!campaign) {
      return;
    }

    const status = String(campaign.status || "").toLowerCase();
    const hidden = Boolean(campaign.hidden);

    if (status !== "active" || hidden) {
      console.log("Skipping notification for campaign", {
        campaignId: event.params.campaignId,
        status: campaign.status,
        hidden: campaign.hidden,
      });
      return;
    }

    const title = "New Campaign Available";
    const body = campaign.title || "A new campaign is now available.";

    await admin.messaging().send({
      topic: "campaigns",
      data: {
        title,
        body,
        campaignId: event.params.campaignId,
        screen: "home",
      },
      android: {
        priority: "high",
      },
    });

    console.log("Campaign notification sent", {
      campaignId: event.params.campaignId,
      title: campaign.title,
    });
  }
);
