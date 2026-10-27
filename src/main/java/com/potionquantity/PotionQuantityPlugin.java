package com.potionquantity;

import com.google.common.base.Strings;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

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
		public Widget name;
		public Widget dosesOriginal;
		public Widget potDisplay;
		public Widget doseDisplay;
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
			Widget wName = children[i + 2];
			Widget wDoses = children[i + 3];

			//Skip past errors
			if (Strings.isNullOrEmpty(wName.getText()) || Strings.isNullOrEmpty(wDoses.getText())) {
				continue;
			}

			// Hide vanilla quantity text
			wDoses.setHidden(true);
			Widget potText = w.createChild(WidgetType.TEXT);
			Widget doseText = w.createChild(WidgetType.TEXT);

			for (Widget text : new Widget[]{potText, doseText})
			{
				text.setOriginalHeight(wDoses.getOriginalHeight());
				text.setOriginalWidth(wDoses.getOriginalWidth());
				text.setFontId(wDoses.getFontId());
				text.setTextColor(wDoses.getTextColor());
				text.setTextShadowed(true);

				// Start both at vanilla position
				text.setOriginalX(wDoses.getOriginalX());
				text.setOriginalY(wDoses.getOriginalY());

				text.revalidate();
			}

			PotionPanel panel = new PotionPanel();
			panel.name = wName;
			panel.dosesOriginal = wDoses;
			panel.potDisplay = potText;
			panel.doseDisplay = doseText;
			potionPanels.add(panel);
		}

		updateProgressBars();
	}

	private void updateProgressBars() {
		for (PotionPanel panel : potionPanels) {
			String str = panel.dosesOriginal.getText();
			str = str.replace(",", "");

			boolean isUnf = panel.name.getText().contains("(unf)");
			boolean isWeaponPoison = panel.name.getText().contains("Weapon poison");
			boolean isPoultice = panel.name.getText().contains("poultice");

			int fullDoses;
			if (isUnf || isWeaponPoison || isPoultice) {
				fullDoses = 1;
			} else {
				//Get how many doses the potion is set to withdraw
				int startIndex = panel.name.getText().indexOf("(") + 1;
				int endIndex = startIndex + 1;

				fullDoses = Integer.parseInt(panel.name.getText().substring(startIndex, endIndex));
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

			// Reset text positions
			panel.potDisplay.setOriginalY(panel.dosesOriginal.getOriginalY());
			panel.doseDisplay.setOriginalY(panel.dosesOriginal.getOriginalY());

			panel.potDisplay.setText("");
			panel.doseDisplay.setText("");

			switch(config.doseDisplay()) {
				case POTS:
					panel.potDisplay.setText("Pots: " + potCountStr);
					break;
				case DOSES:
					panel.doseDisplay.setText("Doses: " + doseCountStr);
					break;

				case POTS_AND_DOSES:
					panel.potDisplay.setOriginalY(panel.dosesOriginal.getOriginalY() - 5);
					panel.doseDisplay.setOriginalY(panel.dosesOriginal.getOriginalY() + 5);
					panel.potDisplay.setText("Pots: " + potCountStr);
					panel.doseDisplay.setText("Doses: " + doseCountStr);
					break;

				case DOSES_AND_POTS:
					panel.doseDisplay.setOriginalY(panel.dosesOriginal.getOriginalY() - 5);
					panel.potDisplay.setOriginalY(panel.dosesOriginal.getOriginalY() + 5);
					panel.doseDisplay.setText("Doses: " + doseCountStr);
					panel.potDisplay.setText("Pots: " + potCountStr);
					break;
			}

			panel.potDisplay.revalidate();
			panel.doseDisplay.revalidate();
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event) {
		if (rebuildPotions) {
			updateProgressBars();
			rebuildPotions = false;
		}
	}
}