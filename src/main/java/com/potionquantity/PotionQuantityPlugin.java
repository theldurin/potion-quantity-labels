package com.potionquantity;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.widgets.Widget;
import com.google.common.base.Strings;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.callback.ClientThread;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@PluginDescriptor(
	name = "Potion Quantity Labels"
)

public class PotionQuantityPlugin extends Plugin  {
	@Inject
	private ClientThread clientThread;

	@Inject
	private Client client;

	@Inject
	private PotionQuantityConfig config;

	@Inject
	private ItemManager itemManager;

    private boolean rebuildPotions;

	static class PotionPanel {
		public Widget item;
		public Widget dosesOriginal;
		public Widget dosesDisplay;
	}

	private final List<PotionPanel> potionPanels = new ArrayList<>();

	@Provides
	PotionQuantityConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(PotionQuantityConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged) {
		if (configChanged.getGroup().equals(PotionQuantityConfig.GROUP)) {
			clientThread.invokeLater(this::updateProgressBars);
		}
	}

	@Override
	protected void shutDown() throws UnsupportedOperationException {
		potionPanels.clear();
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event) {
		int scriptId = event.getScriptId();
		//If Potion Storage is opened
		if (scriptId == ScriptID.POTIONSTORE_BUILD )  {
			createProgressBars();
		}

		if (scriptId == ScriptID.POTIONSTORE_WITHDRAW_DOSES)  {
			rebuildPotions = true;
		}
	}

	private void createProgressBars() {
		potionPanels.clear();

		Widget w = client.getWidget(InterfaceID.Bankmain.POTIONSTORE_ITEMS);
        assert w != null;
        Widget[] children = w.getDynamicChildren();

		//Iterate through all potions in storage
		for (int i = 0; i + 4 < children.length; i += 5) {
			Widget wItem = children[i + 1];
			Widget wDoses = children[i + 3];

			//Skip past errors
			if (wItem.getItemId() == -1 || Strings.isNullOrEmpty(wDoses.getText())) {
				continue;
			}

			Widget text = w.createChild(WidgetType.TEXT);
			text.setOriginalHeight(wDoses.getOriginalHeight());
			text.setOriginalWidth(wDoses.getOriginalWidth());
			text.setOriginalX((wDoses.getOriginalX()) + 2);
			text.setOriginalY((wDoses.getOriginalY()) + 1);
			text.setFontId((wDoses.getFontId()));
			text.setTextColor((wDoses.getTextColor()));
			text.setTextShadowed(true);
			text.revalidate();

			PotionPanel panel = new PotionPanel();
			panel.item = wItem;
			panel.dosesOriginal = wDoses;
			panel.dosesDisplay = text;
			potionPanels.add(panel);
		}

		updateProgressBars();
	}

	private void updateProgressBars() {
		for (PotionPanel panel : potionPanels) {
			String str = panel.dosesOriginal.getText();
			str = str.replace(",", "");

			boolean isUnf = panel.item.getName().contains("(unf)");
			boolean isWeaponPoison = panel.item.getName().contains("Weapon poison");
			boolean isPoultice = panel.item.getName().contains("poultice");

			int fullDoses;
			if (isUnf || isWeaponPoison || isPoultice) {
				fullDoses = 1;
			} else {
				//Get how many doses the potion is set to withdraw
				int startIndex = panel.item.getName().indexOf("(") + 1;
				int endIndex = startIndex + 1;

				fullDoses = Integer.parseInt(panel.item.getName().substring(startIndex, endIndex));
			}

			//Get how many doses
			int doseCount;
			if (isUnf || isWeaponPoison || isPoultice) {
				doseCount = Integer.parseInt(str.replace("Quantity: ", ""));
			} else {
				doseCount = Integer.parseInt(str.replace("Doses: ", ""));
			}

			//Update doses text
			String doseText = "";
			int potCount = (int)Math.floor((float)doseCount/(float)fullDoses);

			String doseCountStr = String.format("%,d", doseCount);
			String potCountStr = String.format("%,d", potCount);

			switch(config.doseDisplay()) {
				case POTS:
					doseText = "Pots: " + potCountStr;
					break;
				case DOSES:
					doseText = "Doses: " + doseCountStr;
					break;

				case POTS_AND_DOSES:
					doseText = "Pots: " + potCountStr + " (" + doseCountStr + ")";
					break;

				case DOSES_AND_POTS:
					doseText = "Doses: " + doseCountStr + " (" + potCountStr + ")";
					break;
			}

			panel.dosesDisplay.setText(doseText);
			panel.dosesDisplay.revalidate();
		}
	}

	@Subscribe
	public void onClientTick() {
		if (rebuildPotions) {
			updateProgressBars();
			rebuildPotions = false;
		}
	}
}
