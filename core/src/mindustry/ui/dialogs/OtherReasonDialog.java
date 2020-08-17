package mindustry.ui.dialogs;

import arc.Core;
import arc.scene.ui.TextArea;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import mindustry.entities.type.Player;
import mindustry.gen.Call;
import mindustry.gen.Icon;
import mindustry.gen.Tex;

public class OtherReasonDialog extends FloatingDialog{

    private TextField reason;
    private Runnable task;

    public OtherReasonDialog(){
        super("Other Reason");
        reason = new TextField();
        cont.clear();
        cont.defaults().size(500f, 64f);
        cont.add(reason);
        cont.row();
        addCloseButton();
        addOkButton(task);
        setFillParent(false);
    }

    public void show(Player user, int bantime) {
        reason.clearText();
        task = () -> {
            Call.sendChatMessage("/tempban #" + user.id + " " + bantime + " " + reason.getText());
            Core.app.post(this::hide);
        };
        buttons.clear();
        addCloseButton();
        addOkButton(task);
        show();
    }
}