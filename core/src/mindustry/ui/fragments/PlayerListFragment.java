package mindustry.ui.fragments;

import arc.*;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.Vars;
import mindustry.core.GameState.*;
import mindustry.entities.type.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.net.*;
import mindustry.net.Packets.*;
import mindustry.ui.*;

import static mindustry.Vars.*;
import static mindustry.Vars.content;

public class PlayerListFragment extends Fragment{
    private boolean visible = false;
    private Table content = new Table().marginRight(13f).marginLeft(13f);
    private Interval timer = new Interval();
    private TextField sField;

    @Override
    public void build(Group parent){
        parent.fill(cont -> {
            cont.visible(() -> visible);
            cont.update(() -> {
                if(!(net.active() && !state.is(State.menu))){
                    visible = false;
                    return;
                }

                if(visible && timer.get(20)){
                    rebuild();
                    content.pack();
                    content.act(Core.graphics.getDeltaTime());
                    //TODO hack
                    Core.scene.act(0f);
                }
            });

            cont.table(Tex.buttonTrans, pane -> {
                pane.label(() -> Core.bundle.format(playerGroup.size() == 1 ? "players.single" : "players", playerGroup.size()));
                pane.row();
                sField = pane.addField(null, text -> {
                    rebuild();
                }).grow().pad(8).get();
                sField.setMaxLength(maxNameLength);
                sField.setMessageText(Core.bundle.format("players.search"));
                pane.row();
                pane.pane(content).grow().get().setScrollingDisabled(true, false);
                pane.row();

                pane.table(menu -> {
                    menu.defaults().growX().height(50f).fillY();

                    menu.addButton("$server.bans", ui.bans::show).disabled(b -> net.client());
                    menu.addButton("$server.admins", ui.admins::show).disabled(b -> net.client());
                    menu.addButton("$close", this::toggle);
                }).margin(0f).pad(10f).growX();

            }).touchable(Touchable.enabled).margin(14f);
            cont.left();
        });

        rebuild();
    }

    public void rebuild(){
        content.clear();

        float h = 74f;

        playerGroup.all().sort(Structs.comparing(Unit::getTeam));
        playerGroup.all().each(user -> {
            NetConnection connection = user.con;

            if (connection == null && net.server() && !user.isLocal) return;
            if (sField.getText().length() > 0 && !user.name.toLowerCase().contains(sField.getText().toLowerCase()) && !Strings.stripColors(user.name.toLowerCase()).contains(sField.getText().toLowerCase()))
                return;

            Table button = new Table();
            button.left();
            button.margin(5).marginBottom(10);

            Table table = new Table() {
                @Override
                public void draw() {
                    super.draw();
                    Draw.color(Pal.gray);
                    Draw.alpha(parentAlpha);
                    Lines.stroke(Scl.scl(4f));
                    Lines.rect(x, y, width, height);
                    Draw.reset();
                }
            };
            table.margin(8);
            table.add(new Image(user.getIconRegion()).setScaling(Scaling.none)).grow();

            button.add(table).size(h / 2);
            button.labelWrap("[#" + user.color.toString().toUpperCase() + "]" + user.name).width(170f).pad(10);
            button.add().grow();

            button.addImage(Icon.admin).visible(() -> user.isAdmin && !(!user.isLocal && net.server())).padRight(5).get().updateVisibility();

            if ((net.server() || player.isAdmin) && !user.isLocal && (!user.isAdmin || net.server())) {
                // custom
                button.add().growY();

                float bs = (h) / 2f;
                button.addImageButton(Icon.hammerSmall.tint(Color.green), Styles.clearPartiali,
                    () -> ui.showConfirm("$confirm", "$confirmkick", () -> Call.sendChatMessage("/tempban #" + user.id + " 0" + " AFK"))).size(h / 2);

                button.addImageButton(Icon.hammerSmall.tint(Color.yellow), Styles.clearPartiali,
                    () -> {
                        int ref = -1;
                        if (user.getTeam().equals(player.getTeam())) {
                            ui.chatfrag.addMessage("Undo actions -> Player: " + user.name + "\n", null);
                            ref = griefWarnings.refs.get(user);
                        }
                        ui.showKickConfirm("$confirm", "$confirmkick", null,
                            () -> Call.sendChatMessage("/tempban #" + user.id + " 24" + " mass"),
                            () -> Call.sendChatMessage("/tempban #" + user.id + " 24" + " power"),
                            () -> Call.sendChatMessage("/tempban #" + user.id + " 24" + " micro"),
                            () -> ui.otherReason.show(user, 24), ref, user.name);
                    }).size(h / 2);

                button.addImageButton(Icon.hammerSmall.tint(Color.orange), Styles.clearPartiali,
                    () -> {
                        int ref = -1;
                        if (user.getTeam().equals(player.getTeam())) {
                            ui.chatfrag.addMessage("Undo actions -> Player: " + user.name + "\n", null);
                            ref = griefWarnings.refs.get(user);
                        }
                        ui.showKickConfirm("$confirm", "$confirmkick", null,
                            () -> Call.sendChatMessage("/tempban #" + user.id + " 168" + " mass"),
                            () -> Call.sendChatMessage("/tempban #" + user.id + " 168" + " power"),
                            () -> Call.sendChatMessage("/tempban #" + user.id + " 168" + " micro"),
                            () -> ui.otherReason.show(user, 168), ref, user.name);
                    }).size(h / 2);

                button.addImageButton(Icon.hammerSmall.tint(Color.red), Styles.clearPartiali,
                    () -> {
                        int ref = -1;
                        if (user.getTeam().equals(player.getTeam())) {
                            ui.chatfrag.addMessage("Undo actions -> Player: " + user.name + "\n", null);
                            ref = griefWarnings.refs.get(user);
                        }
                        ui.showKickConfirm("$confirm", "$confirmkick", null,
                                () -> Call.sendChatMessage("/ban #" + user.id + " mass"),
                                () -> Call.sendChatMessage("/ban #" + user.id + " power"),
                                () -> Call.sendChatMessage("/ban #" + user.id + " micro"),
                                () -> Call.sendChatMessage("/ban #" + user.id + " others"), ref, user.name);
                    }).size(h / 2);

                button.addImageButton(Icon.zoom, Styles.clearTogglePartiali, () -> {
                    // reset freecam
                    for(Player player2: playerGroup){
                        if(player2.onKick) {
                            // reset freecam
                            player2.onKick = false;
                        }
                    }
                    // pause building
                    player.isBuilding = false;
                    player.buildWasAutoPaused = false;
                    ui.chatfrag.addMessage("Showing player " + griefWarnings.formatPlayer(user), null);
                    user.onKick = true;
                    // griefWarnings.auto.setFreecam(true, user.x, user.y);
                }).size(h / 2);

            }else if(!user.isLocal && !user.isAdmin && net.client() && playerGroup.size() >= 1){
                button.add().growY();
                //votekick
                if(Core.settings.getBool("whitehammeronly")) {
                    button.addImageButton(Icon.hammerSmall.tint(Color.white), Styles.clearPartiali,
                            () -> ui.showConfirm("$confirm", "$confirmkick", () -> Call.sendChatMessage("/votekick #" + user.id))).size(h / 2);
                }else{
                    button.addImageButton(Icon.hammerSmall.tint(Color.green), Styles.clearPartiali,
                            () -> ui.showConfirm("$confirm", "$confirmkick", () -> Call.sendChatMessage("/tempban #" + user.id + " 0" + " AFK"))).size(h / 2);

                    button.addImageButton(Icon.hammerSmall.tint(Color.yellow), Styles.clearPartiali,
                            () -> {
                                int ref = -1;
                                if (user.getTeam().equals(player.getTeam())) {
                                    ui.chatfrag.addMessage("Undo actions -> Player: " + user.name + "\n", null);
                                    ref = griefWarnings.refs.get(user);
                                }
                                ui.showKickConfirm("$confirm", "$confirmkick", null,
                                        () -> Call.sendChatMessage("/tempban #" + user.id + " 1" + " mass"),
                                        () -> Call.sendChatMessage("/tempban #" + user.id + " 1" + " power"),
                                        () -> Call.sendChatMessage("/tempban #" + user.id + " 1" + " micro"),
                                        () -> ui.otherReason.show(user, 1), ref, user.name);
                            }).size(h / 2);

                    button.addImageButton(Icon.hammerSmall.tint(Color.orange), Styles.clearPartiali,
                            () -> {
                                int ref = -1;
                                if (user.getTeam().equals(player.getTeam())) {
                                    ui.chatfrag.addMessage("Undo actions -> Player: " + user.name + "\n", null);
                                    ref = griefWarnings.refs.get(user);
                                }
                                ui.showKickConfirm("$confirm", "$confirmkick", null,
                                        () -> Call.sendChatMessage("/tempban #" + user.id + " 24" + " mass"),
                                        () -> Call.sendChatMessage("/tempban #" + user.id + " 24" + " power"),
                                        () -> Call.sendChatMessage("/tempban #" + user.id + " 24" + " micro"),
                                        () -> ui.otherReason.show(user, 24), ref, user.name);
                            }).size(h / 2);

                    button.addImageButton(Icon.hammerSmall.tint(Color.red), Styles.clearPartiali,
                            () -> {
                                int ref = -1;
                                if (user.getTeam().equals(player.getTeam())) {
                                    ui.chatfrag.addMessage("Undo actions -> Player: " + user.name + "\n", null);
                                    ref = griefWarnings.refs.get(user);
                                }
                                ui.showKickConfirm("$confirm", "$confirmkick", null,
                                        () -> Call.sendChatMessage("/tempban #" + user.id + " 168" + " mass"),
                                        () -> Call.sendChatMessage("/tempban #" + user.id + " 168" + " power"),
                                        () -> Call.sendChatMessage("/tempban #" + user.id + " 168" + " micro"),
                                        () -> ui.otherReason.show(user, 168), ref, user.name);
                            }).size(h / 2);
                }
                button.addImageButton(Icon.zoom, Styles.clearTogglePartiali, () -> {
                    // reset freecam
                    for(Player player2: playerGroup){
                        if(player2.onKick) {
                            // reset freecam
                            player2.onKick = false;
                        }
                    }
                    // pause building
                    player.isBuilding = false;
                    player.buildWasAutoPaused = false;
                    ui.chatfrag.addMessage("Showing player " + griefWarnings.formatPlayer(user), null);
                    user.onKick = true;
                    // griefWarnings.auto.setFreecam(true, user.x, user.y);
                }).size(h/2);
            }
            content.add(button).padBottom(-6).width(350f).maxHeight(h + 14);
            content.row();
            content.addImage().height(4f).color(state.rules.pvp ? user.getTeam().color : Pal.gray).growX();
            content.row();
        });

        if(sField.getText().length() > 0 && !playerGroup.all().contains(user -> user.name.toLowerCase().contains(sField.getText().toLowerCase()))) {
            content.add(Core.bundle.format("players.notfound")).padBottom(6).width(350f).maxHeight(h + 14);
        }

        content.marginBottom(5);
    }

    public void toggle(){
        visible = !visible;
        if(visible){
            rebuild();
        }else{
            Core.scene.setKeyboardFocus(null);
            sField.clearText();
        }
    }

}
