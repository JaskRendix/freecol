/**
 *  Copyright (C) 2002-2024   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.client.gui.dialog;

import static net.sf.freecol.common.util.CollectionUtils.transform;

import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.DialogHandler;
import net.sf.freecol.client.gui.FontLibrary;
import net.sf.freecol.client.gui.panel.FreeColButton;
import net.sf.freecol.client.gui.panel.FreeColButton.ButtonStyle;
import net.sf.freecol.client.gui.panel.FreeColPanel;
import net.sf.freecol.client.gui.panel.MigPanel;
import net.sf.freecol.client.gui.panel.Utility;
import net.sf.freecol.common.debug.FreeColDebugger;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTradeItem;
import net.sf.freecol.common.model.DiplomaticTrade;
import net.sf.freecol.common.model.DiplomaticTrade.TradeContext;
import net.sf.freecol.common.model.DiplomaticTrade.TradeStatus;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoldTradeItem;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsContainer;
import net.sf.freecol.common.model.GoodsLocation;
import net.sf.freecol.common.model.GoodsTradeItem;
import net.sf.freecol.common.model.InciteTradeItem;
import net.sf.freecol.common.model.NationSummary;
import net.sf.freecol.common.model.Ownable;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.StanceTradeItem;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.TradeItem;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitTradeItem;


/**
 * The panel that allows negotiations between players.
 * <p>
 * Panel Layout:
 * <p>
 * The Negotiation Dialog is laid out in three columns. The outer two columns are 30% of the panel's width while the
 *      center column is 40% the panel's width.
 * <p style="display: block; font-family: monospace; white-space: pre; margin: 1em 0;">
 * --------------------------------------------------------------------------
 * |                         negotiationDialog.title                        |
 * | ---------------------------------------------------------------------- |
 * |       Left Pane      |       Center Pane       |      Right Pane       |
 * |                      |      (centerPanel)      |                       |
 * | ---------------------------------------------------------------------- |
 * |                       [cancelButton][sendButton]                       |
 * --------------------------------------------------------------------------
 * <p>
 * The Left and Right Panes are laid out as follows:
 * <p style="display: block; font-family: monospace; white-space: pre; margin: 1em 0;">
 * | --------------- |
 * | gold            |
 * | --------------- |
 * | colony          |
 * | --------------- |
 * | stance          |
 * | --------------- |
 * | war declaration |
 * | --------------- |
 * TODO: Improve layout description
 */
public final class NegotiationDialog extends FreeColPanel {

    private static final Logger logger = Logger.getLogger(NegotiationDialog.class.getName());

    private static final int HUGE_DEMAND = 100000;

    private static final String ADD = "add";
    private static final String CLEAR = "clear";

    /** The other player in the negotiation (!= getMyPlayer()). */
    private final Player otherPlayer;

    /** The agreement under negotiation. */
    private final DiplomaticTrade agreement;

    /** A comment message. */
    private final StringTemplate comment;

    /** The panels for various negotiable data. */
    private StanceTradeItemPanel stancePanel;
    private GoldTradeItemPanel goldOfferPanel, goldDemandPanel;
    private ColonyTradeItemPanel colonyOfferPanel, colonyDemandPanel;
    private GoodsTradeItemPanel goodsOfferPanel, goodsDemandPanel;
    private InciteTradeItemPanel inciteOfferPanel, inciteDemandPanel;
    private UnitTradeItemPanel unitOfferPanel, unitDemandPanel;


    /** A panel showing a summary of the current agreement. */
    private JPanel summary;

    /** Useful internal messages. */
    private StringTemplate demand, offer;
    private String exchangeMessage;

    /** Responses. */
    private FreeColButton send = null, accept = null;


    /**
     * Creates a new {@code NegotiationDialog} instance.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     * @param frame The owner frame.
     * @param our Our {@code FreeColGameObject} that is negotiating.
     * @param other The other {@code FreeColGameObject}.
     * @param agreement The {@code DiplomaticTrade} agreement that
     *     is being negotiated.
     * @param comment An optional {@code StringTemplate}
     *     commentary message.
     */
    public NegotiationDialog(FreeColClient freeColClient,
                             FreeColGameObject our, FreeColGameObject other,
                             DiplomaticTrade agreement, StringTemplate comment,
                             DialogHandler<DiplomaticTrade> handler) {
        super(freeColClient, null, new MigLayout("wrap 3, fill", "[][growprio 200][align right]", ""));

        // 1. Initialize State
        this.otherPlayer = ((Ownable)other).getOwner();
        this.agreement = agreement;
        this.comment = comment;
        
        final Player player = getMyPlayer();
        final Unit ourUnit = (our instanceof Unit) ? (Unit)our : null;
        final Colony ourColony = (our instanceof Colony) ? (Colony)our : null;

        // 2. Build Components
        setupTemplates(player);
        StringTemplate tutorial = setupTradePanels(player, our, ourUnit, ourColony);
        setupSummaryPanel();

        // 3. Build Layout
        buildLayout(tutorial);

        // 4. Configure Buttons
        buildButtons(handler);

        updateDialog(false);
        setSize(getPreferredSize());
    }

    /**
     * Sets up the string templates for demand, offer, and exchange.
     */
    private void setupTemplates(Player player) {
        StringTemplate nation = player.getCountryLabel();
        StringTemplate otherNation = otherPlayer.getCountryLabel();
        
        this.demand = StringTemplate.template("negotiationDialog.demand")
                .addStringTemplate("%nation%", nation)
                .addStringTemplate("%otherNation%", otherNation);
        this.offer = StringTemplate.template("negotiationDialog.offer")
                .addStringTemplate("%nation%", nation)
                .addStringTemplate("%otherNation%", otherNation);
        this.exchangeMessage = Messages.message("negotiationDialog.exchange");
    }

    /**
     * Initializes all TradeItemPanels based on the TradeContext.
     * @return An optional tutorial StringTemplate.
     */
    private StringTemplate setupTradePanels(Player player, FreeColGameObject our, Unit ourUnit, Colony ourColony) {
        // Initial Gold Panels (common to all)
        NationSummary ns = igc().nationSummary(otherPlayer);
        int gold = (ns == null || ns.getGold() == Player.GOLD_NOT_ACCOUNTED) 
                ? HUGE_DEMAND : ns.getGold();
        this.goldDemandPanel = new GoldTradeItemPanel(otherPlayer, gold);

        gold = (player.getGold() == Player.GOLD_NOT_ACCOUNTED) 
                ? HUGE_DEMAND : player.getGold();
        this.goldOfferPanel = new GoldTradeItemPanel(player, gold);

        StringTemplate tutorial = null;
        TradeContext context = agreement.getContext();

        switch (context) {
            case CONTACT:
                if (getFreeColClient().tutorialMode()) {
                    tutorial = StringTemplate.key("negotiationDialog.contact.tutorial");
                }
                this.stancePanel = new StanceTradeItemPanel(player, otherPlayer);
                this.inciteOfferPanel = new InciteTradeItemPanel(player, otherPlayer);
                this.inciteDemandPanel = new InciteTradeItemPanel(otherPlayer, player);
                break;

            case DIPLOMATIC:
                this.stancePanel = new StanceTradeItemPanel(player, otherPlayer);
                this.colonyDemandPanel = new ColonyTradeItemPanel(otherPlayer);
                this.colonyOfferPanel = new ColonyTradeItemPanel(player);
                this.inciteOfferPanel = new InciteTradeItemPanel(player, otherPlayer);
                this.inciteDemandPanel = new InciteTradeItemPanel(otherPlayer, player);
                break;

            case TRADE:
                List<Goods> goods = getAnyGoods();
                this.goodsDemandPanel = new GoodsTradeItemPanel(otherPlayer, goods);
                
                GoodsLocation gl = (ourUnit != null) ? ourUnit : ourColony;
                goods = (ourUnit != null) ? ourUnit.getGoodsList() : ourColony.getCompactGoodsList();
                
                for (Goods g : goods) {
                    if (g.getAmount() > GoodsContainer.CARGO_SIZE) {
                        g.setAmount(GoodsContainer.CARGO_SIZE);
                    }
                    g.setLocation(gl);
                }
                this.goodsOfferPanel = new GoodsTradeItemPanel(player, goods);
                this.unitDemandPanel = new UnitTradeItemPanel(otherPlayer, getUnitUnitList(null));
                this.unitOfferPanel = new UnitTradeItemPanel(player, 
                        ((ourUnit != null) ? getUnitUnitList(ourUnit) : ourColony.getUnitList()));
                break;

            case TRIBUTE:
                this.stancePanel = new StanceTradeItemPanel(player, otherPlayer);
                this.inciteOfferPanel = new InciteTradeItemPanel(player, otherPlayer);
                this.inciteDemandPanel = new InciteTradeItemPanel(otherPlayer, player);
                break;

            default:
                throw new IllegalStateException("Bogus trade context: " + context);
        }
        return tutorial;
    }

    private void setupSummaryPanel() {
        this.summary = new MigPanel(new MigLayout("wrap 2", "[20px:n:n][]"));
        this.summary.setOpaque(false);
        this.summary.add(Utility.localizedTextArea(comment), "center, span 2");
    }

    /**
     * Build Layout of Diplomatic Trade Dialog
     */
    private void buildLayout(StringTemplate tutorial) {
        // Main Panel Header
        add(Utility.localizedHeader("negotiationDialog.title." + agreement.getContext().getKey(),
                Utility.FONTSPEC_TITLE), "span 3, center");

        // Panel contents Header row
        Font font = FontLibrary.getScaledFont("normal-bold-tiny");
        
        JTextArea labelDemandMessage = Utility.localizedTextArea(this.demand);
        labelDemandMessage.setFont(font);
        add(labelDemandMessage, "width 50:50:100%, grow");

        JTextArea blank = new JTextArea(" ");
        blank.setVisible(false);
        add(blank, "");

        JTextArea labelOfferMessage = Utility.localizedTextArea(this.offer);
        labelOfferMessage.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        labelOfferMessage.setFont(font);
        add(labelOfferMessage, "width 50:50:100%, grow");

        // Panel contents
        add(this.goldDemandPanel, "sg item"); // Left pane
        
        JPanel centerPanel = new MigPanel(new MigLayout("wrap 1, fill"));
        centerPanel.setOpaque(false);
        
        if (tutorial != null) {
            JTextArea tutArea = Utility.localizedTextArea(tutorial, 30);
            centerPanel.add(tutArea, "top, wmin 200");
        }
        
        JScrollPane scroll = new JScrollPane(this.summary,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        centerPanel.add(scroll, "top, width 100%, wmin 200, grow");
        
        add(centerPanel, "spany, top, growx"); // Center pane
        add(this.goldOfferPanel, "sg item"); // Right pane

        // Add remaining paired panels
        addPair(colonyDemandPanel, colonyOfferPanel);
        if (stancePanel != null) add(stancePanel, "skip, sg item");
        addPair(goodsDemandPanel, goodsOfferPanel);
        addPair(inciteDemandPanel, inciteOfferPanel);
        addPair(unitDemandPanel, unitOfferPanel);

        if (FreeColDebugger.isInDebugMode(FreeColDebugger.DebugMode.MENUS)) {
            add(new JLabel("Version = " + agreement.getVersion()));
        }
    }

    private void addPair(JComponent demand, JComponent offer) {
        if (demand != null) {
            add(demand, "sg item");
            add(offer, "sg item");
        }
    }

    private void buildButtons(DialogHandler<DiplomaticTrade> handler) {
        int numButtons = 0;
        
        // Accept Button: A new offer can not be accepted (version > 0)
        if (agreement.getVersion() > 0) {
            accept = createNegotiationButton("negotiationDialog.accept", TradeStatus.ACCEPT_TRADE, handler);
            accept.withButtonStyle(ButtonStyle.IMPORTANT);
            okButton = accept;
            numButtons++;
        }

        // Send/Propose Button
        send = createNegotiationButton("negotiationDialog.send", TradeStatus.PROPOSE_TRADE, handler);
        if (accept == null) {
            send.withButtonStyle(ButtonStyle.IMPORTANT);
            okButton = send;
        }
        numButtons++;

        // Cancel/Reject Button
        if (agreement.getVersion() > 0 || agreement.getContext() != TradeContext.CONTACT) {
            final FreeColButton cancel = createNegotiationButton("negotiationDialog.cancel", TradeStatus.REJECT_TRADE, handler);
            numButtons++;
            
            setEscapeAction(new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    cancel.doClick();
                }
            });
            
            // Layout buttons
            add(send, "newline, span 3, split " + numButtons + ((accept == null) ? ", tag ok " : ", tag next"));
            if (accept != null) add(accept, "tag ok");
            add(cancel, "tag cancel");
        } else {
            add(send, "newline, span 3, split " + numButtons + ", tag ok");
        }
    }

    /**
     * Helper to create buttons with standard negotiation behavior.
     */
    private FreeColButton createNegotiationButton(String localeKey, TradeStatus status, DialogHandler<DiplomaticTrade> handler) {
        FreeColButton btn = new FreeColButton(Messages.message(localeKey));
        btn.addActionListener(ae -> {
            getGUI().removeComponent(this);
            agreement.setStatus(status);
            handler.handle(agreement);
        });
        return btn;
    }

    /**
     * Gets a list of all possible storable goods (one cargo load).
     * Note that these goods are fictional.  They are the goods that
     * *might* be in the other player's store.  Therefore they have a
     * bogus location (i.e. not the actual goods container).
     *
     * @return A list of storable {@code Goods}.
     */
    private List<Goods> getAnyGoods() {
        Game game = getGame();
        return getSpecification().getStorableGoodsTypeList().stream()
                .map(gt -> new Goods(game, null, gt, GoodsContainer.CARGO_SIZE))
                .collect(Collectors.toList());
    }

    /**
     * Get a list of units to offer that are associated with a given unit.
     *
     * @param unit The {@code Unit} that is trading.
     * @return A list of {@code Unit}s.
     */
    private List<Unit> getUnitUnitList(Unit unit) {
        if (unit == null) return new ArrayList<>();

        // If the unit is a carrier or on one, we want the whole group.
        // Otherwise, it's just the single unit.
        if (unit.isCarrier()) {
            return new ArrayList<>(unit.getUnitList());
        } else if (unit.isOnCarrier()) {
            return new ArrayList<>(unit.getCarrier().getUnitList());
        }
        
        return Collections.singletonList(unit);
    }

    /**
     * Update the entire dialog.
     *
     * @param changed If true, the agreement has changed, and the accept button
     * needs to be disabled
     */
    private void updateDialog(boolean changed) {
        refresh(goldOfferPanel);
        refresh(goldDemandPanel);
        refresh(stancePanel);
        refresh(colonyOfferPanel);
        refresh(colonyDemandPanel);
        refresh(goodsOfferPanel);
        refresh(goodsDemandPanel);
        refresh(inciteOfferPanel);
        refresh(inciteDemandPanel);
        refresh(unitOfferPanel);
        refresh(unitDemandPanel);

        if (changed && this.accept != null) {
            this.accept.setEnabled(false);
        }

        updateSummary();
        revalidate();
        repaint();
    }

    public interface TradeItemPanel {
        void update(DiplomaticTrade trade);
    }

    /**
     * A private helper to handle null checks and call the specific 
     * update logic for our inner panels.
     */
    private void refresh(TradeItemPanel panel) {
        if (panel != null) {
            panel.update(agreement);
        }
    }


    /**
     * Gets a trade item button for a given item.
     *
     * @param item The {@code TradeItem} to make a button for.
     * @param saleDir Boolean to indicate the EU price for sale (T) or buy (F)
     * @return A new {@code JButton} for the item.
     */
    private JButton getTradeItemButton(TradeItem item, boolean saleDir) {
        var market = getMyPlayer().getMarket();
        JButton button = new JButton(new RemoveAction(item));
        
        Goods goods = item.getGoods();
        String label = Messages.message(item.getLabel());

        // If the item contains goods, append the European market valuation
        if (goods != null) {
            String templateKey = saleDir ? "negotiationDialog.euSalePrice" : "negotiationDialog.euBuyPrice";
            int priceTotal = saleDir 
                ? market.getSalePrice(goods.getType(), goods.getAmount())
                : market.getBidPrice(goods.getType(), goods.getAmount());

            String priceLabel = Messages.message(StringTemplate
                    .template(templateKey)
                    .addAmount("%priceTotal%", priceTotal));
            
            button.setText(label + " " + priceLabel);
        } else {
            // Non-goods items use the standard label
            button.setText(label);
        }
        
        // Style the button to look like a clickable link
        button.setMargin(Utility.EMPTY_MARGIN);
        button.setOpaque(false);
        button.setForeground(Utility.getLinkColor());
        button.setBorder(Utility.blankBorder(0, 0, 0, 0));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        return button;
    }

    /**
     * Update the text summary of the proposed transaction.
     */
    private void updateSummary() {
        Player player = getMyPlayer();

        summary.removeAll();

        // Add the AI/Negotiation comment at the top
        summary.add(Utility.localizedTextArea(comment), "center, span 2");

        // Process Player Offers
        List<TradeItem> offers = agreement.getItemsGivenBy(player);
        if (!offers.isEmpty()) {
            summary.add(Utility.localizedLabel(this.offer), "span");
            for (TradeItem item : offers) {
                summary.add(getTradeItemButton(item, true), "skip");
            }
        }

        // Process Player Demands (Items from the other player)
        List<TradeItem> demands = agreement.getItemsGivenBy(otherPlayer);
        if (!demands.isEmpty()) {
            // Determine the header: if there are no offers, just show "Demand".
            // If there are both, show the "In exchange for" message.
            if (offers.isEmpty()) {
                summary.add(Utility.localizedLabel(this.demand), "span");
            } else {
                summary.add(new JLabel(exchangeMessage), "newline 20, span");
            }
            
            for (TradeItem item : demands) {
                summary.add(getTradeItemButton(item, false), "skip");
            }
        }
        
        // Ensure the UI re-layouts after modification
        summary.revalidate();
        summary.repaint();
    }

    /**
     * Remove trade items of a given type.
     *
     * @param itemClass The class of {@code TradeItem} to remove.
     */
    public void removeTradeItems(Class<? extends TradeItem> itemClass) {
        this.agreement.removeType(itemClass);
        updateDialog(true);
    }

    /**
     * Adds a {@code ColonyTradeItem} to the list of TradeItems.
     *
     * @param source The source {@code Player}.
     * @param colony The {@code Colony} to add.
     */
    public void addColonyTradeItem(Player source, Colony colony) {
        Player player = getMyPlayer();
        Player destination = (source == player) ? otherPlayer : player;

        agreement.add(new ColonyTradeItem(getGame(), source, destination, colony));
        updateDialog(true);
    }

    /**
     * Adds a {@code GoldTradeItem} to the list of TradeItems.
     *
     * @param source The source {@code Player}.
     * @param amount The amount of gold.
     */
    public void addGoldTradeItem(Player source, int amount) {
        if (amount <= 0) return;

        Player player = getMyPlayer();
        Player destination = (source == player) ? otherPlayer : player;

        agreement.add(new GoldTradeItem(getGame(), source, destination, amount));
        updateDialog(true);
    }

    /**
     * Adds a {@code GoodsTradeItem} to the list of TradeItems.
     *
     * @param source The source {@code Player}.
     * @param goods The {@code Goods} to add.
     */
    public void addGoodsTradeItem(Player source, Goods goods) {
        Player player = getMyPlayer();
        Player destination = (source == player) ? otherPlayer : player;

        agreement.add(new GoodsTradeItem(getGame(), source, destination, goods));
        updateDialog(true);
    }

    /**
     * Add an {@code InciteTradeItem} to the list of trade items.
     *
     * @param source The source {@code Player}.
     * @param victim The {@code Player} to be attacked.
     */
    public void addInciteTradeItem(Player source, Player victim) {
        Player player = getMyPlayer();
        Player destination = (source == player) ? otherPlayer : player;

        agreement.add(new InciteTradeItem(getGame(), source, destination, victim));
        updateDialog(true);
    }

    /**
     * Trade a stance change between the players.
     *
     * @param stance The {@code Stance} to trade.
     */
    public void addStanceTradeItem(Stance stance) {
        agreement.add(new StanceTradeItem(getGame(), otherPlayer, getMyPlayer(), stance));
        updateDialog(true);
    }

    /**
     * Adds a {@code UnitTradeItem} to the list of TradeItems.
     *
     * @param source The source {@code Player}.
     * @param unit The {@code Unit} to add.
     */
    public void addUnitTradeItem(Player source, Unit unit) {
        Player player = getMyPlayer();
        Player destination = (source == player) ? otherPlayer : player;

        agreement.add(new UnitTradeItem(getGame(), source, destination, unit));
        updateDialog(true);
    }


    // Override Component

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeNotify() {
        super.removeNotify();
        removeAll();
        this.stancePanel = null;
        this.goldOfferPanel = this.goldDemandPanel = null;
        this.colonyOfferPanel = this.colonyDemandPanel = null;
        this.goodsOfferPanel = this.goodsDemandPanel = null;
        this.inciteOfferPanel = this.inciteDemandPanel = null;
        this.unitOfferPanel = this.unitDemandPanel = null;
        this.summary = null;
        this.demand = this.offer = null;
        this.exchangeMessage = null;
    }
    
    
    /**
     * Action to remove a specific {@code TradeItem} from the agreement.
     */
    private class RemoveAction extends AbstractAction {
        private final TradeItem item;

        public RemoveAction(TradeItem item) {
            this.item = item;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void actionPerformed(ActionEvent ae) {
            agreement.remove(item);
            updateDialog(true);
        }
    }

    private class ColonyTradeItemPanel extends MigPanel implements TradeItemPanel {

        private final Player source;
        private final JComboBox<Colony> colonyBox;
        private final JButton clearButton;
        private final JButton addButton;
        private final JLabel label;
        private final List<Colony> allColonies;


        /**
         * Creates a new {@code ColonyTradeItemPanel} instance.
         *
         * @param source The {@code Player} source.
         */
        public ColonyTradeItemPanel(Player source) {
            super(new MigLayout("wrap 1", "", ""));

            this.source = source;
            this.colonyBox = new JComboBox<>();
            
            this.clearButton = button("negotiationDialog.clear", CLEAR, this::onClear);
            this.addButton   = button("negotiationDialog.add",   ADD,   this::onAdd);
            
            this.label = Utility.localizedLabel(Messages.getName("model.tradeItem.colony"));
            this.allColonies = List.copyOf(source.getColonyList());

            setBorder(Utility.getSimpleLineBorder());

            add(this.label);
            add(this.colonyBox);
            add(this.clearButton, "split 2");
            add(this.addButton);

            setSize(getPreferredSize());
        }

        /**
         * Helper to create localized buttons with modern lambda actions.
         */
        private JButton button(String key, String command, Runnable action) {
            JButton b = Utility.localizedButton(key);
            b.setActionCommand(command);
            b.addActionListener(e -> action.run());
            return b;
        }

        /**
         * Update this panel.
         *
         * @param dt The {@code DiplomaticTrade} to update with.
         */
        @Override
        public void update(DiplomaticTrade dt) {
            if (!source.isEuropean()) return;

            ActionListener[] listeners = colonyBox.getActionListeners();
            for (ActionListener al : listeners) {
                colonyBox.removeActionListener(al);
            }

            // Start from the original colony list — never mutate it
            List<Colony> available = new ArrayList<>(allColonies);

            // Remove colonies already included in the trade
            for (Colony c : dt.getColoniesGivenBy(source)) {
                available.remove(c); // safe even if not present
            }

            colonyBox.removeAllItems();
            for (Colony c : available) colonyBox.addItem(c);

            boolean enable = !available.isEmpty();
            clearButton.setEnabled(!enable);
            addButton.setEnabled(enable);
            colonyBox.setEnabled(enable);
            label.setEnabled(enable);

            for (ActionListener al : listeners) {
                colonyBox.addActionListener(al);
            }
        }

        private void onAdd() {
            Colony selected = (Colony) colonyBox.getSelectedItem();
            if (selected != null) {
                NegotiationDialog.this.addColonyTradeItem(source, selected);
            }
        }

        private void onClear() {
            NegotiationDialog.this.removeTradeItems(ColonyTradeItem.class);
        }
    }

    private class GoldTradeItemPanel extends MigPanel
            implements ActionListener, TradeItemPanel {

        private final Player source;
        private final JSpinner spinner;


        /**
         * Creates a new {@code GoldTradeItemPanel} instance.
         *
         * @param source The {@code Player} that is trading.
         * @param gold The maximum amount of gold to trade.
         */
        public GoldTradeItemPanel(Player source, int gold) {
            super(new MigLayout("wrap 1", "", ""));

            this.source = source;
            this.spinner = new JSpinner(new SpinnerNumberModel(0, 0, gold, 1));
            JButton clearButton = Utility.localizedButton("negotiationDialog.clear");
            clearButton.addActionListener(this);
            clearButton.setActionCommand(CLEAR);
            JButton addButton = Utility.localizedButton("negotiationDialog.add");
            addButton.addActionListener(this);
            addButton.setActionCommand(ADD);
            // adjust entry size
            ((JSpinner.DefaultEditor)this.spinner.getEditor())
                                                 .getTextField()
                                                 .setColumns(5);

            setBorder(Utility.getSimpleLineBorder());

            add(Utility.localizedLabel(Messages.getName("model.tradeItem.gold")));
            add(Utility.localizedLabel(StringTemplate
                    .template("negotiationDialog.goldAvailable")
                    .addAmount("%amount%", gold)));
            add(this.spinner);
            add(clearButton, "split 2");
            add(addButton);

            setSize(getPreferredSize());
        }


        /**
         * Update this panel to reflect the current state of the agreement.
         *
         * @param dt The {@code DiplomaticTrade} agreement.
         */
        @Override
        public void update(DiplomaticTrade dt) {
            // Get the gold amount assigned to the player associated with this panel
            int gold = dt.getGoldGivenBy(this.source);
            
            if (gold >= 0) {
                SpinnerNumberModel model = (SpinnerNumberModel)spinner.getModel();
                
                // Ensure the spinner maximum doesn't block the AI's demand
                if (gold > (Integer)model.getMaximum()) {
                    model.setMaximum(gold); 
                }
                
                // Update the visual value in the UI
                model.setValue(gold);
            }
        }


        // Implement ActionListener

        /**
         * {@inheritDoc}
         */
        @Override
        public void actionPerformed(ActionEvent ae) {
            final String command = ae.getActionCommand();
            if (null != command) switch (command) {
                case ADD:
                    int amount = ((Integer)spinner.getValue());
                    NegotiationDialog.this.addGoldTradeItem(source, amount);
                    break;
                case CLEAR:
                    NegotiationDialog.this.removeTradeItems(GoldTradeItem.class);
                    break;
                default:
                    logger.warning("Bad command: " + command);
                    break;
            }
        }
    }

    private class GoodsTradeItemPanel extends MigPanel implements TradeItemPanel {

        private class GoodsBoxRenderer extends JLabel
                implements ListCellRenderer<Goods> {

            /**
             * {@inheritDoc}
             */
            @Override
            public Component getListCellRendererComponent(JList<? extends Goods> list,
                                                          Goods value, int index, boolean isSelected,
                                                          boolean cellHasFocus) {
                setText((value == null) ? ""
                        : Messages.message(value.getLabel(true)));
                return this;
            }
        }

        private final Player source;
        private final JComboBox<Goods> goodsBox;
        private final JButton clearButton;
        private final JButton addButton;
        private final JLabel label;
        private final List<Goods> allGoods;


        /**
         * Creates a new {@code GoodsTradeItemPanel} instance.
         *
         * @param source The {@code Player} nominally in possession of the
         * goods (this may be totally fictional).
         * @param allGoods The {@code Goods} to trade.
         */
        public GoodsTradeItemPanel(Player source, List<Goods> allGoods) {
            super(new MigLayout("wrap 1", "", ""));

            this.source = source;
            this.goodsBox = new JComboBox<>(new DefaultComboBoxModel<Goods>());
            this.goodsBox.setRenderer(new GoodsBoxRenderer());
            
            this.clearButton = button("negotiationDialog.clear", CLEAR, this::onClear);
            this.addButton   = button("negotiationDialog.add",   ADD,   this::onAdd);
            
            this.label = Utility.localizedLabel(Messages.nameKey("model.tradeItem.goods"));
            this.allGoods = allGoods;

            setBorder(Utility.getSimpleLineBorder());

            add(this.label);
            add(this.goodsBox);
            add(this.clearButton, "split 2");
            add(this.addButton);

            setSize(getPreferredSize());
        }

        /**
         * Helper to create localized buttons with modern lambda actions.
         */
        private JButton button(String key, String command, Runnable action) {
            JButton b = Utility.localizedButton(key);
            b.setActionCommand(command);
            b.addActionListener(e -> action.run());
            return b;
        }

        /**
         * Update this panel.
         *
         * @param dt The {@code DiplomaticTrade} to update with.
         */
        @Override
        public void update(DiplomaticTrade dt) {
            ActionListener[] listeners = this.goodsBox.getActionListeners();
            for (ActionListener al : listeners) {
                this.goodsBox.removeActionListener(al);
            }

            List<Goods> available = allGoods.stream()
                    .map(g -> new Goods(g.getGame(), null, g.getType(), g.getAmount()))
                    .collect(Collectors.toList());

            for (Goods traded : dt.getGoodsGivenBy(source)) {
                for (int i = 0; i < available.size(); i++) {
                    Goods g = available.get(i);
                    if (g.getType() == traded.getType()) {
                        int remaining = g.getAmount() - traded.getAmount();
                        if (remaining <= 0) {
                            available.remove(i);
                        } else {
                            g.setAmount(remaining);
                        }
                        break;
                    }
                }
            }

            goodsBox.removeAllItems();
            for (Goods g : available) goodsBox.addItem(g);

            boolean enable = !available.isEmpty();
            label.setEnabled(enable);
            clearButton.setEnabled(!enable);
            addButton.setEnabled(enable);
            goodsBox.setEnabled(enable);

            for (ActionListener al : listeners) {
                this.goodsBox.addActionListener(al);
            }
        }

        private void onAdd() {
            Goods selected = (Goods) goodsBox.getSelectedItem();
            if (selected != null) {
                NegotiationDialog.this.addGoodsTradeItem(source, selected);
            }
        }

        private void onClear() {
            NegotiationDialog.this.removeTradeItems(GoodsTradeItem.class);
        }
    }

    private class InciteTradeItemPanel extends MigPanel implements TradeItemPanel {

        private class InciteBoxRenderer extends JLabel
                implements ListCellRenderer<Player> {

            /**
             * {@inheritDoc}
             */
            @Override
            public Component getListCellRendererComponent(JList<? extends Player> list,
                                                          Player value, int index, boolean isSelected,
                                                          boolean cellHasFocus) {
                setText((value == null) ? ""
                        : Messages.message(value.getNationLabel()));
                return this;
            }
        }

        private final Player source;
        private final Player other;
        private final JComboBox<Player> victimBox;
        private final JLabel label;
        private final JButton clearButton;
        private final JButton addButton;
        private final List<Player> available = new ArrayList<>();


        /**
         * Creates a new {@code InciteTradeItemPanel} instance.
         *
         * @param source The {@code Player} that is trading.
         * @param other The {@code Player} negotiated with.
         */
        public InciteTradeItemPanel(Player source, Player other) {
            super(new MigLayout("wrap 1", "", ""));

            this.source = source;
            this.other = other;
            this.victimBox = new JComboBox<>(new DefaultComboBoxModel<Player>());
            this.victimBox.setRenderer(new InciteBoxRenderer());

            this.clearButton = button("negotiationDialog.clear", CLEAR, this::onClear);
            this.addButton   = button("negotiationDialog.add",   ADD,   this::onAdd);

            this.label = Utility.localizedLabel(Messages.nameKey("model.tradeItem.incite"));

            setBorder(Utility.getSimpleLineBorder());

            available.clear();
            final Predicate<Player> incitablePred = p ->
                    p != this.other && this.source.getStance(p).isIncitable();
            available.addAll(transform(getGame().getLivePlayers(this.source),
                    incitablePred));

            add(this.label);
            add(this.victimBox);
            add(this.clearButton, "split 2");
            add(this.addButton);

            setSize(getPreferredSize());
        }

        /**
         * Helper to create localized buttons with modern lambda actions.
         */
        private JButton button(String key, String command, Runnable action) {
            JButton b = Utility.localizedButton(key);
            b.setActionCommand(command);
            b.addActionListener(e -> action.run());
            return b;
        }

        /**
         * Update this panel.
         *
         * @param dt The {@code DiplomaticTrade} to update with.
         */
        @Override
        public void update(DiplomaticTrade dt) {
            // Remove all action listeners, so the update has no
            // effect (except updating the list).
            ActionListener[] listeners = this.victimBox.getActionListeners();
            for (ActionListener al : listeners) {
                this.victimBox.removeActionListener(al);
            }

            this.victimBox.removeAllItems();
            for (Player p : available) victimBox.addItem(p);

            boolean enable = !available.isEmpty();
            this.label.setEnabled(enable);
            this.clearButton.setEnabled(!enable);
            this.addButton.setEnabled(enable);
            this.victimBox.setEnabled(enable);

            // Restore action listeners
            for (ActionListener al : listeners) {
                this.victimBox.addActionListener(al);
            }
        }

        private void onAdd() {
            Player victim = (Player) victimBox.getSelectedItem();
            if (victim != null) {
                NegotiationDialog.this.addInciteTradeItem(source, victim);
            }
        }

        private void onClear() {
            NegotiationDialog.this.removeTradeItems(InciteTradeItem.class);
        }
    }

    /**
     * Class for the stance trade panel. Access needs to be public so
     * that comboBoxLabel() is externally visible.
     */
    public class StanceTradeItemPanel extends MigPanel implements TradeItemPanel {

        private class StanceBoxRenderer extends JLabel
                implements ListCellRenderer<Stance> {

            /**
             * {@inheritDoc}
             */
            @Override
            public Component getListCellRendererComponent(JList<? extends Stance> list,
                                                          Stance value, int index, boolean isSelected,
                                                          boolean cellHasFocus) {
                setText((value == null) ? "" : Messages.getName(value));
                return this;
            }
        }

        private final Player source;
        private final Player target;
        private final JComboBox<Stance> stanceBox;
        private final JButton clearButton;
        private final JButton addButton;


        /**
         * Creates a new {@code StanceTradeItemPanel} instance.
         *
         * @param source The {@code Player} offering the stance change.
         * @param target The {@code Player} to consider the stance change.
         */
        public StanceTradeItemPanel(Player source, Player target) {
            super(new MigLayout("wrap 1", "", ""));

            this.source = source;
            this.target = target;
            this.stanceBox = new JComboBox<>(new DefaultComboBoxModel<Stance>());
            this.stanceBox.setRenderer(new StanceBoxRenderer());
            
            this.clearButton = button("negotiationDialog.clear", CLEAR, this::onClear);
            this.addButton   = button("negotiationDialog.add",   ADD,   this::onAdd);

            setBorder(Utility.getSimpleLineBorder());

            add(Utility.localizedLabel(Messages.nameKey("model.tradeItem.stance")));
            add(this.stanceBox);
            add(this.clearButton, "split 2");
            add(this.addButton);
        }

        /**
         * Helper to create localized buttons with modern lambda actions.
         */
        private JButton button(String key, String command, Runnable action) {
            JButton b = Utility.localizedButton(key);
            b.setActionCommand(command);
            b.addActionListener(e -> action.run());
            return b;
        }

        /**
         * Select the item with a given stance.
         *
         * @param stance The {@code Stance} to select.
         */
        private void setSelectedValue(Stance stance) {
            for (int i = 0; i < stanceBox.getItemCount(); i++) {
                if (stanceBox.getItemAt(i) == stance) {
                    stanceBox.setSelectedIndex(i);
                    break; // Optimization: stop once found
                }
            }
        }

        /**
         * Update this panel with a given trade.
         *
         * @param dt The {@code DiplomaticTrade} to update with.
         */
        @Override
        public void update(DiplomaticTrade dt) {
            stanceBox.removeAllItems();

            Stance stance = source.getStance(target);
            if (stance != Stance.WAR) {
                stanceBox.addItem(Stance.WAR);
            } else {
                stanceBox.addItem(Stance.CEASE_FIRE);
            }
            if (stance != Stance.PEACE) {
                stanceBox.addItem(Stance.PEACE);
            }
            if (stance == Stance.PEACE) {
                stanceBox.addItem(Stance.ALLIANCE);
            }

            Stance select = dt.getStance();
            if (select != null) setSelectedValue(select);
        }

        private void onAdd() {
            Stance stance = (Stance) stanceBox.getSelectedItem();
            if (stance != null) {
                NegotiationDialog.this.addStanceTradeItem(stance);
            }
        }

        private void onClear() {
            NegotiationDialog.this.removeTradeItems(StanceTradeItem.class);
        }
    }

    private class UnitTradeItemPanel extends MigPanel implements TradeItemPanel {

        private class UnitBoxRenderer extends JLabel
                implements ListCellRenderer<Unit> {

            /**
             * {@inheritDoc}
             */
            @Override
            public Component getListCellRendererComponent(JList<? extends Unit> list,
                                                          Unit value, int index, boolean isSelected,
                                                          boolean cellHasFocus) {
                setText((value == null) ? "" : value.getDescription());
                return this;
            }
        }

        private final Player source;
        private final JComboBox<Unit> unitBox;
        private final JButton clearButton;
        private final JButton addButton;
        private final JLabel label;
        private final List<Unit> allUnits;


        /**
         * Creates a new {@code UnitTradeItemPanel} instance.
         *
         * @param source The {@code Player} nominally in posession of the
         * unit (this may be totally fictional).
         * @param allUnits The {@code Unit}s to trade.
         */
        public UnitTradeItemPanel(Player source, List<Unit> allUnits) {
            super(new MigLayout("wrap 1", "", ""));

            this.source = source;
            this.unitBox = new JComboBox<>(new DefaultComboBoxModel<Unit>());
            this.unitBox.setRenderer(new UnitBoxRenderer());
            
            this.clearButton = button("negotiationDialog.clear", CLEAR, this::onClear);
            this.addButton   = button("negotiationDialog.add",   ADD,   this::onAdd);
            
            this.label = Utility.localizedLabel(Messages.nameKey("model.tradeItem.unit"));
            this.allUnits = allUnits;

            setBorder(Utility.getSimpleLineBorder());

            add(this.label);
            add(this.unitBox);
            add(this.clearButton, "split 2");
            add(this.addButton);

            setSize(getPreferredSize());
        }

        /**
         * Helper to create localized buttons with modern lambda actions.
         */
        private JButton button(String key, String command, Runnable action) {
            JButton b = Utility.localizedButton(key);
            b.setActionCommand(command);
            b.addActionListener(e -> action.run());
            return b;
        }

        /**
         * Update this panel with a given trade.
         *
         * @param dt The {@code DiplomaticTrade} to update with.
         */
        @Override
        public void update(DiplomaticTrade dt) {
            // Remove all action listeners, so the update has no
            // effect (except updating the list).
            ActionListener[] listeners = unitBox.getActionListeners();
            for (ActionListener al : listeners) {
                unitBox.removeActionListener(al);
            }

            List<Unit> available = new ArrayList<>(allUnits);
            for (Unit u : dt.getUnitsGivenBy(source)) {
                // Remove the ones already on the table
                if (available.contains(u)) {
                    available.remove(u);
                } else {
                    allUnits.add(u); // Did not know about this!
                }
            }

            unitBox.removeAllItems();
            for (Unit u : available) unitBox.addItem(u);

            boolean enable = !available.isEmpty();
            this.label.setEnabled(enable);
            clearButton.setEnabled(!enable);
            addButton.setEnabled(enable);
            unitBox.setEnabled(enable);

            // Restore action listeners
            for (ActionListener al : listeners) {
                unitBox.addActionListener(al);
            }
        }

        private void onAdd() {
            Unit selected = (Unit) unitBox.getSelectedItem();
            if (selected != null) {
                NegotiationDialog.this.addUnitTradeItem(source, selected);
            }
        }

        private void onClear() {
            NegotiationDialog.this.removeTradeItems(UnitTradeItem.class);
        }
    }
}
