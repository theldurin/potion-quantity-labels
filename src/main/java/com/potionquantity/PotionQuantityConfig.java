package com.potionquantity;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(PotionQuantityConfig.GROUP)
public interface PotionQuantityConfig extends Config  {
	String GROUP = "potionQuantityLabels";

	enum doseDisplayType {
		POTS,
		DOSES,
		POTS_AND_DOSES,
		DOSES_AND_POTS
	}

	@ConfigItem(
			keyName = "doseDisplay",
			name = "Text Display",
			description = "What value the text is tracking"
	)
	default doseDisplayType doseDisplay() {
		return doseDisplayType.POTS;
	}
}
