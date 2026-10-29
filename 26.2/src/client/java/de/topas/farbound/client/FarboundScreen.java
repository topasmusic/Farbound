package de.topas.farbound.client;

import de.topas.farbound.*;
import de.topas.farbound.FarboundUiLayout.Rect;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
import static de.topas.farbound.client.FarboundTheme.*;

/** The approved dashboard, drawn in its original coordinates and uniformly fitted to the screen. */
public final class FarboundScreen extends Screen {
    private FarboundUiLayout layout;
    private final List<Help> help = new ArrayList<>();
    private final List<AbstractWidget> modalButtons=new ArrayList<>();
    private int onlinePage, recoveryPage;
    private String coordinateX = "0", coordinateZ = "0", coordinateError = "";
    private String confirmation, acknowledgedNotice = "", dropdown;
    private List<Option> options = List.of();
    private Rect menuBounds;
    private Consumer<String> choose;
    private boolean friends, pendingLaunch;
    private int lastRevision = -1, pendingRevision, menuSelection;
    private long pendingSince;
    private UiButton newExpeditionButton;
    private record Help(Rect bounds, String title, String body) {}
    private record Option(String label, String value) {}
    public FarboundScreen() { super(Component.literal("Farbound")); }
    private FarboundNetwork.View state() { return FarboundClient.view; }
    private boolean draft() { return state().status.equals("DRAFT") && !state().invited; }
    private boolean compactParty(){return state().phase.equals("ACTIVE")||state().phase.equals("PAUSED");}
    public void update() {
        // Periodic state packets must not replace an editor or reset its selection.
        // Changed settings and server notices still invalidate the modal normally.
        if ("coordinates".equals(confirmation) && lastRevision == state().revision && !showNotice() && modalButtons.stream().anyMatch(w -> w instanceof CoordinateBox)) return;
        int focusX = getFocused() instanceof AbstractWidget w ? w.getX() : -1;
        int focusY = getFocused() instanceof AbstractWidget w ? w.getY() : -1;
        if (lastRevision != state().revision) { confirmation = null; pendingLaunch = false; closeMenu(); }
        if (!state().notice.isEmpty()) pendingLaunch = false;
        if (showNotice()) closeMenu();
        if (state().members.size() > 1) friends = true;
        rebuildWidgets();
        if (focusX >= 0) for(var child:children()) if(child instanceof AbstractWidget w && w.getX()==focusX && w.getY()==focusY && w.active) {setFocused(w);break;}
    }
    @Override protected void init() {
        clearWidgets(); help.clear(); modalButtons.clear();
        newExpeditionButton = null;
        layout = FarboundUiLayout.fit(width,height);
        var v = state(); lastRevision = v.revision;
        boolean editable = draft() && v.leader;
        button(984,39,40,34,"×",this::onClose,true,Style.QUIET,"Close","Close this window. Your expedition keeps its current state.");
        if(v.admin)button(32,45,170,30,"Server tools",()->confirm("admin"),true,Style.QUIET,"Server tools","Inspect the open expedition, recover your profile or manage rules with operator permission.");
        if (!v.status.equals("NONE")) {
            if(draft()||v.invited){
            toggle(216,148,v.homeward,true,editable,()->config("mode",v.homeward?"restart":"homeward"),v.homeward?"Required ("+v.homeRadius+" blocks)":"Optional",
                "Require travelling home","On: finish within "+v.homeRadius+" horizontal blocks of your personal departure point, in the same dimension. Off: return with Finish expedition. Pausing never completes the homeward challenge.");
            select(216,200,386,v.customDestination?"Custom coordinates":number(v.distance)+" blocks","distance",List.of(new Option("10,000 blocks","10000"),new Option("50,000 blocks","50000"),new Option("100,000 blocks","100000"),new Option("1,000,000 blocks","1000000"),new Option("Custom coordinates…","custom")),editable,
                "Distance","A safe destination is searched for between 100% and 110% of this distance. Server limits still apply.");
            if(v.customDestination)button(216,251,386,40,"X: "+v.customX+"  Z: "+v.customZ+" · Edit",()->confirm("coordinates"),editable,Style.NORMAL,"Custom coordinates","Safe Overworld surface within 100 blocks of this target. Height is automatic. Configured protected areas still apply.");
            else segments(216,251,386,"My location","World spawn",!v.worldSpawn,
                () -> config("reference","here"),() -> config("reference","spawn"),editable,
                "Measure the destination distance from the leader's current Overworld location. Each participant keeps their own home.",
                "Measure the destination distance from the world's spawn point. Each participant still returns to their own home.");
            select(232,374,370,inventoryName(v.inventory),"inventory",List.of(new Option("Store at home","STORE"),new Option("Take everything","KEEP")),draft(),
                inventoryName(v.inventory),inventoryTip(v.inventory));
            toggle(232,421,v.enderStore,false,draft(),() -> config("ender",v.enderStore?"keep":"store"),v.enderStore?"Store":"Keep",
                v.enderStore?"Ender chest: Store":"Ender chest: Keep",v.enderStore?"Your original ender chest is stored. Start empty and empty it again before restoring the original contents.":"Your current ender chest travels with you. Its contents are never rolled back.");
            toggle(232,463,v.xpStore,false,draft(),() -> config("xp",v.xpStore?"keep":"store"),v.xpStore?"Store":"Keep",
                v.xpStore?"XP: Store":"XP: Keep",v.xpStore?"Start at level 0. Finishing restores your exact home XP. Expedition XP is replaced, not added.":"Keep your current XP throughout the expedition and on return. No XP is stored or rolled back.");
            }else {
                help.add(new Help(new Rect(216,148,386,40),"Travel home","Finish within "+v.homeRadius+" horizontal blocks of your personal departure point, in the same dimension, when required. Pausing does not complete the journey."));
                help.add(new Help(new Rect(216,200,386,40),"Departure distance",v.customDestination?"Custom Overworld surface destination, within 100 blocks of the chosen coordinates. Route settings are fixed after departure.":"The confirmed destination was selected between 100% and 110% of this distance. Route settings are fixed after departure."));
                help.add(new Help(new Rect(216,251,386,40),"Distance reference",v.customDestination?"Safe surface near X "+v.customX+", Z "+v.customZ+". The global world spawn stays unchanged.":"The distance ring used "+(v.worldSpawn?"world spawn":"the leader's departure location")+". Each participant keeps their own home."));
                help.add(new Help(new Rect(232,374,370,40),inventoryName(v.inventory),inventoryTip(v.inventory)));
            }
            boolean rules = !v.invited && v.leader && (draft() || v.status.equals("ACTIVE"));
            toggle(232,514,!v.netherBlocked,true,rules,() -> config("nether",v.netherBlocked?"allowed":"blocked"),v.netherBlocked?"Blocked":"Allowed",
                "Nether","Only expedition members are restricted. Changes during a trip need every active member's approval; a new block waits until everyone returns.");
            toggle(232,556,!v.endBlocked,true,rules,() -> config("end",v.endBlocked?"allowed":"blocked"),v.endBlocked?"Blocked":"Allowed",
                "The End","Allow or block End travel for participants only. Your home inventory and starting options stay fixed during the expedition.");
            info(192,168,"Require travelling home","Home means your departure point in the same dimension, within "+v.homeRadius+" horizontal blocks. This is independent of the distance reference.");
            info(151,220,"Distance","The destination lies between 100% and 110% of the selected distance.");
            info(151,271,"Reference point",v.customDestination?"Custom target in the Overworld. Height is automatic; safe spawn within 100 blocks.":"Use the leader's location or world spawn as the center of the distance ring.");
            info(255,344,"Gear & rules","These are your personal gear choices. Only the leader proposes group dimension rules. Start choices cannot change after departure.");
            info(200,395,"Inventory","Stored gear stays safe at home. Empty the expedition inventory before restoring it.");
            info(200,439,"Ender chest","Normal ender chests hold expedition supplies until you finish.");
            info(102,481,"Store XP","Start at level 0. Finishing restores your exact home XP.");
            info(200,533,"Nether","Dimension rules affect participants only, never the whole server.");
            info(200,575,"The End","Changes during an active expedition need everyone's approval.");
            group(editable);
            if (draft() || v.status.equals("SEARCHING") || v.status.equals("COUNTDOWN")) {
                select(654,562,340,v.countdown+" sec","countdown",List.of(new Option("No delay","0"),new Option("5 sec","5"),new Option("10 sec","10"),new Option("15 sec","15"),new Option("30 sec","30")),editable,
                    "Start delay","The countdown begins after a safe destination is found. Damage, logout or lost readiness cancels the departure without changing inventory.");
                info(847,532,"Start delay","Time to prepare after the destination is ready. This is not a trip time limit.");
            } else activeControls();
        }
        footer();
        if (confirmation != null || showNotice()) modalControls();
        if (confirmation != null || showNotice()) for(var child:children()) if(child instanceof AbstractWidget w) {
            w.active = modalButtons.contains(w);
            if(w.active && w.getX()==604) setFocused(w);
        }
    }
    private void group(boolean editable) {
        var v = state();
        if(draft()||v.invited)segments(654,148,340,"Solo","Friends",!friends,
            () -> { if (v.members.size() <= 1) { friends=false; update(); } },
            () -> { friends=true; update(); }, draft(),
            "Travel alone. This option is available while you are the only participant.",
            "Invite online friends below. Everyone joins voluntarily, reviews their own gear and marks ready.");
        if(draft()&&friends){
            int pages=Math.max(1,(v.online.size()+4)/5);onlinePage=Math.min(onlinePage,pages-1);
            for(int i=onlinePage*5;i<Math.min(v.online.size(),onlinePage*5+5);i++){
                var candidate=v.online.get(i);boolean joined=v.people.stream().anyMatch(p->p.id().equals(candidate.id()));
                String label=joined?"Joined":candidate.invited()?"Invited":"+";
                button(924,210+(i%5)*34,60,28,label,()->sendAction("invite",candidate.name()),editable&&!joined&&!candidate.invited(),Style.NORMAL,
                    candidate.name(),joined?"Already in your expedition.":candidate.invited()?"Invitation pending. This player joins voluntarily.":"Invite "+candidate.name()+" to this expedition. They must accept and review their own gear.");
            }
            button(654,402,164,30,"Previous",()->{onlinePage--;update();},onlinePage>0,Style.NORMAL,"Previous players","Show the previous five online players.");
            button(830,402,164,30,"Next",()->{onlinePage++;update();},onlinePage+1<pages,Style.NORMAL,"Next players","Show the next five online players.");
        }
        if(v.phase.equals("ACTIVE")){
            button(654,342,340,38,"Finish expedition",()->confirm("end"),true,Style.PRIMARY,"Finish expedition","End this expedition and restore your home profile. Empty stored inventory and ender chest slots first. "+(v.homeward?"Reach the home area before finishing.":"Home travel is optional."));
            button(654,390,340,38,"Pause & return",()->confirm("pause"),v.canPause,Style.NORMAL,
                "Pause & return",v.canPause?"Save expedition position, gear and XP. Return to your home profile, then resume the same expedition later.":"Pause requires Store at home inventory and stored XP. Taken gear cannot be copied into two profiles.");
            button(654,438,340,38,"Emergency return",()->confirm("abort"),true,Style.DANGER,"Emergency return","Abandon this expedition and return home. Empty stored slots first. A required homeward journey counts as abandoned.");
        }else if(v.phase.equals("PAUSED")){
            button(654,342,340,38,"Resume expedition",()->confirm("resume"),true,Style.PRIMARY,"Resume expedition","Save your updated home profile and return to the saved expedition position with expedition gear and XP.");
            button(654,390,340,38,"Abandon expedition",()->confirm("abandon_paused"),true,Style.DANGER,"Abandon paused expedition","Permanently discard the saved expedition gear and XP. Your current home profile remains active.");
        }
        help.add(new Help(new Rect(654,202,340,compactParty()?128:186),"Participants","The leader sets the route. Each member controls their own gear, readiness and return. Offline members keep their stored profile."));
    }
    private void activeControls() {
        var v=state();
        button(654,554,140,40,"Timer: "+(FarboundClient.hud?"On":"Off"),()->{FarboundClient.hud=!FarboundClient.hud;FarboundClient.preferences();update();},true,Style.NORMAL,"Timer HUD","Show or hide your expedition timer. Paused participants do not advance it alone.");
        select(806,554,188,"Opacity: "+opacityName(),"opacity",List.of(new Option("Low","135"),new Option("Medium","195"),new Option("High","235")),true,"Panel opacity","Choose Low, Medium or High background opacity. Saved on this client.");
    }
    private void footer() {
        var v=state();
        button(28,716,180,46,"Close",this::onClose,true,Style.NORMAL,"Close","Close the menu without canceling your trip.");
        if(v.status.equals("NONE")) {
            newExpeditionButton=button(764,716,250,46,newExpeditionLabel(),() -> sendAction("open",""),!v.occupied&&FarboundClient.cooldownSeconds()==0,Style.PRIMARY,"New expedition","You can have one solo or group expedition, including a paused one. Other players can start independent expeditions. The remaining wait updates automatically."); return;
        }
        if(v.invited) {
            button(764,716,250,46,"Accept invitation",() -> sendAction("accept",""),true,Style.PRIMARY,"Join voluntarily","Join the preparation, then review your own gear before marking ready.");
            button(226,724,238,30,"Decline invitation",()->sendAction("decline",""),true,Style.QUIET,"Decline invitation","Decline without changing your profile, then start your own expedition.");
        }
        else if(draft()) {
            button(226,724,180,30,v.leader?"Discard draft":"Leave group",() -> confirm("leave"),true,Style.QUIET,"Leave preparation","No inventory has changed yet. The leader can discard the draft; a member can leave voluntarily.");
            if(v.ready) button(438,724,238,30,"Ready ✓ · Undo",() -> confirm("unready"),!pendingLaunch,Style.QUIET,"Readiness","Withdraw your readiness. A changed setting automatically clears everyone's readiness.");
            button(764,716,250,46,pendingLaunch?"Starting…":v.leader?"Review & start":"Review & ready",() -> confirm("start"),!pendingLaunch,Style.PRIMARY,"Review & start","Review the exact inventory and XP consequences. The leader can start once everyone is online and ready.");
        } else if(v.status.equals("SEARCHING")||v.status.equals("COUNTDOWN")) {
            button(764,716,250,46,"Cancel start",() -> sendAction("cancel",""),v.leader,Style.NORMAL,"Cancel departure","Stop searching or the countdown before any profile change.");
        }
        if(v.proposedNether!=null)button(654,716,340,40,v.voted?"Rules approved":"Review rule change",()->confirm("vote"),!v.voted&&!v.invited,Style.NORMAL,"Rule proposal","Review the proposed dimension rules. Every unfinished participant must approve.");
    }
    private boolean showNotice() { return !state().notice.isEmpty()&&!state().notice.equals(acknowledgedNotice); }
    private void confirm(String action) {
        if(action.equals("coordinates")){coordinateX=Integer.toString(state().customX);coordinateZ=Integer.toString(state().customZ);coordinateError="";}
        confirmation=action;closeMenu();update();
    }
    private void modalControls() {
        // The modal consumes all background mouse/keyboard input; only its final controls are actionable.
        modalButtons.add(button(604,524,224,42,showNotice()?"Got it":"Back",() -> {acknowledgedNotice=state().notice;confirmation=null;update();},true,Style.NORMAL,"Back","Return to the dashboard without changing any profile."));
        if(!showNotice()&&confirmation.equals("admin_inspect")){
            // Inspection is read-only; Back remains the only final action.
        }else if(!showNotice()&&confirmation.equals("admin_recovery")){
            int pages=Math.max(1,(state().recovery.size()+3)/4);recoveryPage=Math.min(recoveryPage,pages-1);
            for(int i=recoveryPage*4;i<Math.min(state().recovery.size(),recoveryPage*4+4);i++){
                var person=state().recovery.get(i);
                modalButtons.add(button(212,334+(i%4)*37,616,32,"Retry "+person.name(),()->{confirmation=null;sendAction("admin_retry",person.id().toString());update();},true,Style.NORMAL,"Recover profile","Retry this online participant with all reconciliation safeguards."));
            }
            modalButtons.add(button(212,486,180,28,"Previous",()->{recoveryPage--;update();},recoveryPage>0,Style.NORMAL,"Previous","Previous participants."));
            modalButtons.add(button(412,486,180,28,"Next",()->{recoveryPage++;update();},recoveryPage+1<pages,Style.NORMAL,"Next","Next participants."));
        }else if(!showNotice()&&confirmation.equals("admin")){
            modalButtons.add(button(212,326,500,30,"Inspect profiles",()->confirm("admin_inspect"),true,Style.NORMAL,"Inspect","Read participant UUIDs and saved states without changing profiles."));
            modalButtons.add(button(212,360,500,30,"Archive untouched draft",()->confirm("admin_discard"),true,Style.NORMAL,"Archive draft","Only untouched preparations may be archived."));
            modalButtons.add(button(212,400,500,30,"Recover online participant",()->confirm("admin_recovery"),true,Style.NORMAL,"Retry recovery","Choose an online participant to reconcile safely. Offline profiles reconcile on login."));
            modalButtons.add(button(212,440,240,30,"Nether: "+(state().netherBlocked?"allow":"block"),()->confirm("admin_nether"),true,Style.NORMAL,"Operator Nether rule","Propose an enforced rule; new blocks wait for a normal return."));
            modalButtons.add(button(472,440,240,30,"End: "+(state().endBlocked?"allow":"block"),()->confirm("admin_end"),true,Style.NORMAL,"Operator End rule","Propose an enforced rule; new blocks wait for a normal return."));
        }else if(!showNotice()&&confirmation.equals("coordinates")){
            var x=addRenderableWidget(new CoordinateBox(212,340,288,42,"X coordinate"));
            var z=addRenderableWidget(new CoordinateBox(524,340,304,42,"Z coordinate"));
            x.setValue(coordinateX);z.setValue(coordinateZ);
            x.setResponder(value->{coordinateX=value;coordinateError="";});z.setResponder(value->{coordinateZ=value;coordinateError="";});
            modalButtons.add(x);modalButtons.add(z);
            modalButtons.add(button(212,524,374,42,"Use coordinates",this::commitConfirmation,true,Style.PRIMARY,"Use coordinates","Apply X and Z. The server finds a safe surface within 100 blocks before changing any profile."));
        }else if(!showNotice())modalButtons.add(button(212,524,374,42,confirmation.equals("start")?(state().leader?"Confirm & start":"Confirm ready"):confirmation.equals("vote")?"Approve these rules":"Confirm",this::commitConfirmation,true,Style.PRIMARY,"Confirm","Apply the reviewed action using the current server revision."));
    }
    private void commitConfirmation() {
        String action=confirmation;
        if(action.equals("coordinates")){
            try{var coords=ExpeditionPolicy.coordinates(coordinateX+" "+coordinateZ);confirmation=null;config("coordinates",coords[0]+" "+coords[1]);update();}
            catch(IllegalArgumentException ex){coordinateError=ex.getMessage();}
            return;
        }
        confirmation=null;
        if(action.equals("start")) {
            if(state().leader) {
                if(state().ready) sendAction("launch","");
                else {pendingLaunch=true;pendingRevision=state().revision;pendingSince=System.currentTimeMillis();sendAction("ready","");}
            } else if(!state().ready) sendAction("ready","");
        } else if(action.equals("admin_nether")||action.equals("admin_end")){boolean nether=action.equals("admin_nether");sendAction("admin_force",(nether?"nether":"end")+" "+((nether?state().netherBlocked:state().endBlocked)?"allowed":"blocked"));}
        else sendAction(action.equals("unready")?"ready":action.equals("abandon_paused")?"abort":action,"");
        update();
    }
    @Override public void tick() {
        super.tick();
        if(newExpeditionButton!=null){
            newExpeditionButton.setMessage(Component.literal(newExpeditionLabel()));
            newExpeditionButton.active=!state().occupied&&FarboundClient.cooldownSeconds()==0;
        }
        if(pendingLaunch) {
            long elapsed=System.currentTimeMillis()-pendingSince;
            if(state().revision!=pendingRevision || elapsed>4000 || !state().notice.isEmpty()) {pendingLaunch=false;update();}
            else if(state().ready && elapsed>=150) {pendingLaunch=false;sendAction("launch","");update();}
        }
    }
    private String newExpeditionLabel(){
        int seconds=FarboundClient.cooldownSeconds();
        return state().occupied?"Expedition already open":seconds>0?"Wait · "+seconds+" sec":"New expedition";
    }
    private void sendAction(String action,String value) {
        // A new request may fail with the same text. Only periodic repeats of the
        // previous response are acknowledged, not the result of another action.
        acknowledgedNotice="";
        FarboundClient.action(action,value);
    }
    private void config(String key,String value) { sendAction("configure",key+" "+value); }
    private void segments(int x,int y,int w,String a,String b,boolean first,Runnable aa,Runnable bb,boolean enabled,String tipA,String tipB) {
        button(x,y,(w-8)/2,40,a,aa,enabled,first?Style.SELECTED:Style.NORMAL,a,tipA);
        button(x+(w+8)/2,y,(w-8)/2,40,b,bb,enabled,!first?Style.SELECTED:Style.NORMAL,b,tipB);
    }
    private void select(int x,int y,int w,String label,String key,List<Option> values,boolean enabled,String title,String tip) {
        button(x,y,w,40,label,() -> {
            if(key.equals(dropdown)){closeMenu();return;} dropdown=key;options=values;choose=value -> {if(key.equals("distance")&&value.equals("custom"))confirm("coordinates");else config(key,value);};
            int menuHeight=values.size()*38;int my=Math.min(y+43,700-menuHeight);
            menuBounds=new Rect(x,my,w,menuHeight);menuSelection=0;
            if(key.equals("opacity"))choose=value->{FarboundClient.opacity=Integer.parseInt(value);FarboundClient.preferences();closeMenu();update();};
        },enabled,Style.SELECT,title,tip);
    }
    private void toggle(int x,int y,boolean on,boolean green,boolean enabled,Runnable action,String label,String title,String tip) {
        var b=button(x,y,270,30,label,action,enabled,Style.TOGGLE,title,tip);b.on=on;b.green=green;
    }
    private UiButton button(int x,int y,int w,int h,String label,Runnable action,boolean enabled,Style style,String title,String tip) {
        var b=addRenderableWidget(new UiButton(x,y,w,h,label,action,style));b.active=enabled;
        help.add(new Help(new Rect(x,y,w,h),title,tip));return b;
    }
    private void info(int x,int y,String title,String body) {help.add(new Help(new Rect(x-12,y-12,24,24),title,body));}
    private void closeMenu(){dropdown=null;options=List.of();menuBounds=null;choose=null;}
    private MouseButtonEvent local(MouseButtonEvent event){return new MouseButtonEvent(layout.x(event.x()),layout.y(event.y()),event.buttonInfo());}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        var e=local(event);
        if(dropdown!=null) {
            if(menuBounds.contains(e.x(),e.y())&&e.button()==InputConstants.MOUSE_BUTTON_LEFT){clickSound();int index=(int)(e.y()-menuBounds.y())/38;var picked=options.get(index);var callback=choose;closeMenu();callback.accept(picked.value());}else closeMenu();
            return true;
        }
        if(confirmation!=null||showNotice()) {
            if(e.button()!=InputConstants.MOUSE_BUTTON_LEFT)return true;
            for(var control:List.copyOf(modalButtons))if(control.isMouseOver(e.x(),e.y())){
                if(control.mouseClicked(e,doubleClick)&&children().contains(control))setFocused(control);
                break;
            }
            return true;
        }
        boolean handled=super.mouseClicked(e,doubleClick);
        // Vanilla focuses the clicked widget after its callback. A callback may have
        // rebuilt the dashboard or opened a modal, so that instance can now be stale.
        if(getFocused() instanceof AbstractWidget old&&!children().contains(old)) {
            AbstractWidget target=null;
            for(var child:children())if(child instanceof AbstractWidget w&&w.active) {
                if(w.getX()==old.getX()&&w.getY()==old.getY()){target=w;break;}
                if((confirmation!=null||showNotice())&&w.getX()==604&&w.getY()==524)target=w;
            }
            setFocused(target);
        }
        return handled;
    }
    @Override public boolean mouseReleased(MouseButtonEvent e){return super.mouseReleased(local(e));}
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){return super.mouseDragged(local(e),dx/layout.scale(),dy/layout.scale());}
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){return super.mouseScrolled(layout.x(x),layout.y(y),sx,sy);}
    @Override public void mouseMoved(double x,double y){super.mouseMoved(layout.x(x),layout.y(y));}
    @Override public boolean charTyped(CharacterEvent event){return (!"coordinates".equals(confirmation)&&confirmation!=null)||showNotice()||dropdown!=null||super.charTyped(event);}
    @Override public boolean keyPressed(KeyEvent event) {
        if(dropdown!=null) {
            if(event.isEscape())closeMenu();
            else if(event.isDown())menuSelection=(menuSelection+1)%options.size();
            else if(event.isUp())menuSelection=(menuSelection+options.size()-1)%options.size();
            else if(event.isConfirmation()){clickSound();var o=options.get(menuSelection);var cb=choose;closeMenu();cb.accept(o.value());}
            return true;
        }
        if(confirmation!=null||showNotice()) {
            if(event.isCycleFocus())return super.keyPressed(event);
            if(event.isEscape()){confirmation=null;acknowledgedNotice=state().notice;update();}
            else if("coordinates".equals(confirmation)){
                if(event.isConfirmation()){commitConfirmation();return true;}
                return super.keyPressed(event);
            }
            else if(event.isSelection())return getFocused()!=null&&getFocused().keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }
    @Override public boolean isPauseScreen(){return false;}
    private static void clickSound(){Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK,1f));}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        if(layout==null)return;
        int mx=(int)layout.x(mouseX),my=(int)layout.y(mouseY);
        g.pose().pushMatrix();g.pose().translate((float)layout.left(),(float)layout.top());g.pose().scale((float)layout.scale());
        try {
            int alpha=Math.max(120,Math.min(240,FarboundClient.opacity));
            frame(g,0,24,1040,756,alpha<<24|0x081C2C,0xFF4299BD,18);
            frame(g,358,0,324,87,alpha<<24|0x0C2236,0xFF4299BD,12);
            g.fill(360,26,680,88,alpha<<24|0x0C2236);
            texture(g,TITLE,380,13,280,44,0,0,512,80,512,80);
            centered(g,state().status.equals("DRAFT")?"New expedition":state().status.equals("NONE")?"Farbound dashboard":state().phase.equals("PAUSED")?"Expedition paused":state().phase.equals("ACTIVE")?"Expedition active":"Preparing departure",520,63,20,0xFF84C9EE);
            panel(g,26,94,594,210,"JOURNEY");panel(g,26,318,594,322,"GEAR & RULES");
            panel(g,632,94,382,398,"GROUP");panel(g,632,506,382,134,draft()||state().status.equals("SEARCHING")||state().status.equals("COUNTDOWN")?"START DELAY":"EXPEDITION");
            labels(g);
            renderMembers(g);
            frame(g,26,654,988,46,0x55213D51,0xFF478AA8,5);
            centered(g,summary(),536,670,15,0xFFB5DEEF);
            super.extractRenderState(g,mx,my,delta);
            if(dropdown!=null)renderMenu(g,mx,my);
            if(confirmation!=null||showNotice())renderModal(g);
            else renderHelp(g,mx,my);
        } finally {g.pose().popMatrix();}
    }
    private void labels(GuiGraphicsExtractor g) {
        String[] labels={"Home rule","Distance","From","Inventory","Ender chest","XP","Nether","End"};
        int[] ys={162,214,265,388,432,474,525,567};
        text(g,"Travel home",54,162,17,TEXT);
        for(int i=1;i<labels.length;i++)text(g,labels[i],54,ys[i],17,TEXT);
        for(Help h:help)if(h.bounds.w()==24){int cx=h.bounds.x()+12,cy=h.bounds.y()+12;circle(g,cx,cy,9,0xFF83B7D0);circle(g,cx,cy,7,0xFF102A3C);g.fill(cx-1,cy-5,cx+1,cy-3,0xFFB6E4F5);g.fill(cx-1,cy-1,cx+1,cy+5,0xFFB6E4F5);}
        if(!draft()&&!state().invited){
            var v=state();
            text(g,v.homeward?"Required · "+v.homeRadius+"-block home area":"Optional · return via Finish",216,162,15,TEXT);
            text(g,v.customDestination?"Custom coordinates":number(v.distance)+"–"+number(v.distance*11L/10)+" blocks",216,214,17,TEXT);
            text(g,v.customDestination?"X: "+v.customX+"  Z: "+v.customZ+" · ±100 blocks":v.worldSpawn?"World spawn":"Leader's departure location",216,265,16,TEXT);
            text(g,inventoryName(v.inventory),232,388,17,TEXT);
            text(g,v.enderStore?"Stored separately":"Travels with you",232,432,17,TEXT);
            text(g,v.xpStore?"Stored separately":"Travels with you",232,474,17,TEXT);
        }
        String note=state().inventory.equals("STORE")?"Start empty. Home gear returns on finish or pause.":state().inventory.equals("KEEP")?"Your gear travels with you. No rollback or pause.":"Legacy empty-inventory profile. No inventory stored.";
        var notes=wrap(note,370,12);for(int i=0;i<Math.min(2,notes.size());i++)text(g,notes.get(i),232,604+i*15,12,MUTED);
        if(state().phase.equals("PAUSED"))centered(g,"Home profile active · resume when ready",824,457,12,MUTED);
        else if(!compactParty()&&state().proposedNether==null)text(g,"Everyone joins voluntarily.",654,457,13,MUTED);
        if(draft()&&friends)text(g,"Online players · page "+(onlinePage+1)+" / "+Math.max(1,(state().online.size()+4)/5),654,438,12,MUTED);
        if(state().phase.equals("PAUSED")){
            centered(g,"Paused · elapsed "+FarboundClient.time(state().elapsedTicks),824,610,14,MUTED);
        }else if(state().status.equals("ACTIVE")) {
            centered(g,"Elapsed: "+FarboundClient.time(state().elapsedTicks),824,610,14,MUTED);
        }
    }
    private void renderMembers(GuiGraphicsExtractor g) {
        boolean compact=compactParty();
        frame(g,654,202,340,compact?128:186,0x55051620,0xA34D748C,3);
        if(compact)centered(g,state().people.size()==1?"Solo expedition":state().people.size()+" expedition members",824,163,17,TEXT);
        if(draft()&&friends){
            var online=state().online;
            for(int i=onlinePage*5;i<Math.min(online.size(),onlinePage*5+5);i++){
                var p=online.get(i);int y=216+(i%5)*34;face(g,p.id(),666,y,20);
                text(g,clip(p.name(),222,14),698,y+4,14,TEXT);
            }
            if(online.isEmpty())text(g,"No online players available.",666,222,14,MUTED);
            return;
        }
        var details=state().people;
        int count=details.size(),step=compact?(count<=4?28:15):count<=4?44:22;
        for(int i=0;i<count&&i<8;i++) {
            var m=details.get(i);int y=(compact?207:212)+i*step;int face=compact?(count<=4?22:13):count<=4?30:18;
            var mc=Minecraft.getInstance();face(g,m.id(),666,y,face);
            String name=mc.player!=null&&mc.player.getUUID().equals(m.id())?"You":m.name();
            float nameSize=compact?(count<=4?15:11):count<=4?17:13;
            text(g,clip(name,168,nameSize),compact?698:count<=4?710:692,y+(compact?3:count<=4?7:3),nameSize,m.online()?TEXT:MUTED);
            String tag=!m.online()?"Offline":m.phase().equals("ACTIVE")?"Exploring":m.phase().equals("PAUSED")?"At home":m.phase().equals("FINISHED")?"Finished":m.ready()?"Ready":m.leader()?"Leader":m.phase().equals("HOME")?"Preparing":m.phase();
            float tagSize=compact&&count>4?9:12;int tagW=textWidth(tag,tagSize)+14;
            frame(g,982-tagW,y+(compact?0:3),tagW,compact?(count<=4?22:14):22,0x55325568,0xA34991AC,2);text(g,tag,989-tagW,y+(compact?3:8),tagSize,CYAN);
        }
    }
    private void face(GuiGraphicsExtractor g,UUID id,int x,int y,int size){
        var mc=Minecraft.getInstance();var info=mc.getConnection()==null?null:mc.getConnection().getPlayerInfo(id);
        if(info!=null){var skin=info.getSkin().body().texturePath();texture(g,skin,x,y,size,size,8,8,8,8,64,64);texture(g,skin,x,y,size,size,40,8,8,8,64,64);}
        else {g.fill(x,y,x+size,y+size,0xFF426780);centered(g,"?",x+size/2,y+3,13,TEXT);}
    }
    private String summary() {
        var v=state();
        if(v.phase.equals("PAUSED"))return "Expedition paused  ·  Home profile active  ·  Resume or abandon this expedition";
        if(v.status.equals("SEARCHING"))return "Searching for a safe destination…  ·  No inventory changed";
        if(v.status.equals("COUNTDOWN"))return "Departure in "+v.remaining+" sec  ·  Stay safe and ready";
        if(v.status.equals("NONE"))return "Your home profile is active  ·  World and builds preserved";
        if(v.invited)return "You have been invited  ·  Join voluntarily, then review your gear";
        if(v.proposedNether!=null)return "PROPOSAL  ·  Nether "+(v.proposedNether?"blocked":"allowed")+"  ·  End "+(v.proposedEnd?"blocked":"allowed")+(v.voted?"  ·  Approved":"  ·  Review to approve");
        if(v.phase.equals("ACTIVE"))return v.homeward?(v.homeReached?"Home area reached  ·  Finish expedition when ready":"Home: "+number(v.homeDistance)+" blocks away  ·  Finish within "+v.homeRadius+" horizontal blocks in your home dimension"):"Explore at your own pace  ·  Finish to return home, or pause to continue later";
        String gear=v.inventory.equals("STORE")?"Home gear stored":v.inventory.equals("KEEP")?"Gear travels with you":"Empty inventory required";
        return (v.homeward?"Home travel required":"Home travel optional")+"  ·  "+destinationSummary(v)+"  ·  "+gear+(v.rulesChanged?"  ·  Rules changed":"");
    }
    private void renderMenu(GuiGraphicsExtractor g,int mx,int my) {
        g.nextStratum();frame(g,menuBounds.x(),menuBounds.y(),menuBounds.w(),menuBounds.h(),0xFA0B2132,0xFF6AB5D6,3);
        for(int i=0;i<options.size();i++){int y=menuBounds.y()+i*38;boolean over=new Rect(menuBounds.x(),y,menuBounds.w(),38).contains(mx,my);if(over||menuSelection==i)g.fill(menuBounds.x()+2,y+1,menuBounds.x()+menuBounds.w()-2,y+37,0xBF1C5470);text(g,options.get(i).label(),menuBounds.x()+14,y+12,16,TEXT);}
    }
    private void renderHelp(GuiGraphicsExtractor g,int mx,int my) {
        Help h=hoveredHelp(mx,my);
        if(h!=null) {
            var lines=wrap(h.body,250,14);int w=278,hg=42+lines.size()*18;
            int x=Math.max(12,Math.min(1040-w-12,mx+18)),y=my+22;if(y+hg>700)y=my-hg-12;y=Math.max(90,y);
            g.nextStratum();frame(g,x,y,w,hg,0xFA071C2C,0xFF4F8FAB,4);text(g,h.title,x+14,y+12,15,TEXT);
            int yy=y+36;for(String line:lines){text(g,line,x+14,yy,14,0xFFB7D6E7);yy+=18;}
        }
    }
    private Help hoveredHelp(int x,int y) {
        if(dropdown!=null) {
            if(!menuBounds.contains(x,y))return null;
            var option=options.get((y-menuBounds.y())/38);
            String body=switch(dropdown) {
                case "inventory" -> inventoryTip(option.value());
                case "opacity" -> option.label()+" background opacity. This preference is saved on your client.";
                case "distance" -> option.value().equals("custom")?"Enter Overworld X and Z. The server selects a safe surface within 100 blocks. No OP permission required.":"Search for a safe destination between "+number(Long.parseLong(option.value()))+" and "+number(Long.parseLong(option.value())*11/10)+" blocks from the selected reference. Server limits apply.";
                case "countdown" -> option.value().equals("0")?"Depart as soon as a safe destination is ready. No countdown delay.":"Wait "+option.value()+" seconds after a safe destination is ready. Damage, logout or lost readiness cancels departure.";
                default -> "";
            };
            return new Help(menuBounds,option.label(),body);
        }
        // Information circles take precedence; only the target under the pointer supplies help.
        for(int i=help.size()-1;i>=0;i--)if(help.get(i).bounds.contains(x,y))return help.get(i);
        return null;
    }
    private void renderModal(GuiGraphicsExtractor g) {
        g.nextStratum();g.fill(1,25,1039,779,0xA005111C);frame(g,184,218,672,372,0xFB0B2336,0xFF62CAE9,10);
        String title;var lines=new ArrayList<String>();var v=state();
        if(showNotice()){title="Action unavailable";lines.addAll(wrap(v.notice,588,18));}
        else {
            title=switch(confirmation){case "coordinates"->"Custom destination";case "start"->"Review your expedition";case "leave"->"Leave preparation?";case "unready"->"Withdraw readiness?";case "vote"->"Review rule change";case "abort"->"Emergency return";case "pause"->"Pause & return";case "resume"->"Resume expedition";case "abandon_paused"->"Abandon paused expedition?";case "admin"->"Server tools";case "admin_discard"->"Archive untouched draft?";case "admin_nether","admin_end"->"Enforce dimension rule?";default->"Finish expedition";};
            if(confirmation.equals("coordinates")){
                lines.add("Overworld X / Z · safe surface within 100 blocks");
            }else if(confirmation.equals("start")){
                lines.add((v.homeward?"Home travel required":"Home travel optional")+" · "+destinationSummary(v));
                lines.add((v.customDestination?"Overworld surface · search radius: 100 blocks":"Reference: "+(v.worldSpawn?"World spawn":"Leader's location"))+" · Home radius: "+v.homeRadius);
                lines.add("Inventory: "+inventoryName(v.inventory)+" · Ender chest: "+(v.enderStore?"stored":"kept"));
                lines.add("XP: "+(v.xpStore?"start at 0; restore exact home XP":"keep current XP"));
                lines.add("Nether: "+(v.netherBlocked?"blocked":"allowed")+" · End: "+(v.endBlocked?"blocked":"allowed")+" · Delay: "+v.countdown+" sec");
                lines.add("Store expedition loot before restoring saved gear.");
                lines.add("Stored inventory starts reset health, hunger and effects.");
                lines.add(v.leader?"All members must be online and ready to depart.":"Only you can confirm your own gear and readiness.");
            }else if(confirmation.equals("vote")){
                lines.add("New Nether rule: "+(Boolean.TRUE.equals(v.proposedNether)?"blocked":"allowed"));
                lines.add("New End rule: "+(Boolean.TRUE.equals(v.proposedEnd)?"blocked":"allowed"));
                lines.add("A new block waits until all participants return.");lines.add("All active members must approve the same proposal.");
            }else if(confirmation.equals("pause")){
                lines.add("Save expedition position, inventory, XP and personal state.");lines.add("Return to your home profile without completing this trip.");lines.add("Your expedition stays open. Other players can travel independently.");lines.add("Stored ender chest contents stay separate; kept contents travel.");
            }else if(confirmation.equals("resume")){
                lines.add("Save your updated home profile and return to the expedition.");lines.add("Your expedition gear, XP and personal state will be restored.");lines.add("A safe saved destination is required. No profiles are merged.");
            }else if(confirmation.equals("abandon_paused")){
                lines.add("Permanently discard saved expedition gear and XP.");lines.add("Your current home profile stays active and unchanged.");lines.add("This counts as abandonment, never a completed homeward trip.");
            }else if(confirmation.startsWith("admin")){
                if(confirmation.equals("admin")){lines.add("Operator tools");}
                else if(confirmation.equals("admin_inspect")){lines.add(v.adminInfo.split(" · ")[0]);lines.addAll(v.inspection);}
                else if(confirmation.equals("admin_recovery"))lines.add("Choose an online participant to retry safely.");
                else lines.addAll(wrap("Operator action. Profile reconciliation and safe draft checks still apply.",588,16));
            }else if(confirmation.equals("leave"))lines.add("No inventory has changed. You can leave safely.");
            else if(confirmation.equals("unready"))lines.add("The group cannot depart until you confirm readiness again.");
            else {lines.add("Empty stored inventory and ender chest slots first.");lines.add("Your saved home gear is returned exactly once.");lines.add(v.xpStore?"Home XP replaces expedition XP; it is not added.":"Your current XP travels with you.");lines.add("Builds and loot left in chests remain in the world.");if(confirmation.equals("abort"))lines.add("A walk home attempt counts as abandoned.");}
        }
        text(g,title,212,244,24,CYAN);boolean inspect="admin_inspect".equals(confirmation);int y=296;for(String line:lines){text(g,clip(line,608,inspect?13:17),212,y,inspect?13:17,TEXT);y+=inspect?23:27;}
        if("coordinates".equals(confirmation)&&!showNotice()){
            text(g,"X",212,318,14,MUTED);text(g,"Z",524,318,14,MUTED);
            text(g,"Height is automatic. World border and protected areas apply.",212,406,14,MUTED);
            var errors=wrap(coordinateError,600,14);for(int i=0;i<Math.min(3,errors.size());i++)text(g,errors.get(i),212,444+i*18,14,0xFFFFB6A8);
        }
        // Render only the modal's controls over the scrim, not the dashboard a second time.
        for(var b:modalButtons)b.extractRenderState(g,-1,-1,0);
    }
    private static String destinationSummary(FarboundNetwork.View v){return v.customDestination?"X "+v.customX+", Z "+v.customZ+" (±100)":number(v.distance)+"–"+number(v.distance*11L/10)+" blocks";}
    private static String opacityName(){return FarboundClient.opacity<165?"Low":FarboundClient.opacity<215?"Medium":"High";}
    private static String inventoryName(String value){return switch(value){case "KEEP"->"Take everything";case "EMPTY"->"Legacy empty profile";default->"Store at home";};}
    private static String inventoryTip(String value){return switch(value){case "KEEP"->"Take your current items, armor and offhand with you. No storage or item rollback on return.";case "EMPTY"->"Empty your inventory, armor and offhand before departure. Nothing is stored; expedition items stay with you on return.";default->"Protect your original inventory, armor and offhand at home. Start empty and empty these slots before restoring the originals.";};}
    private static String number(long value){return String.format(Locale.US,"%,d",value);}
    private static String clip(String text,int width,float size){return Minecraft.getInstance().font.plainSubstrByWidth(text,(int)(width*9/size));}
    private static List<String> wrap(String text,int width,float size){var out=new ArrayList<String>();String line="";for(String word:text.split("\\s+")){String next=line.isEmpty()?word:line+" "+word;if(textWidth(next,size)>width&&!line.isEmpty()){out.add(line);line=word;}else line=next;}if(!line.isEmpty())out.add(line);return out;}
    private enum Style {NORMAL,SELECTED,PRIMARY,QUIET,DANGER,SELECT,TOGGLE}
    private static final class UiButton extends Button {
        final Style style;boolean on,green;
        UiButton(int x,int y,int w,int h,String label,Runnable action,Style style){super(x,y,w,h,Component.literal(label),b->action.run(),DEFAULT_NARRATION);this.style=style;}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
            int x=getX(),y=getY(),w=getWidth(),h=getHeight();boolean hover=active&&isHoveredOrFocused();
            if(style==Style.TOGGLE){FarboundTheme.toggle(g,x,y,on,green,active);text(g,getMessage().getString(),x+62,y+6,17,active?TEXT:MUTED);if(hover)g.horizontalLine(x+62,x+w-3,y+h-2,0xFF53A9CE);return;}
            int fill=style==Style.PRIMARY?0xFF48C9F4:style==Style.SELECTED?0xE0286B8E:style==Style.QUIET||style==Style.DANGER?0x00000000:0x8A193448;
            int edge=style==Style.PRIMARY?0xFF8DE6FF:style==Style.SELECTED?0xFF4EDCFF:style==Style.QUIET||style==Style.DANGER?0x00000000:0xFF54768D;
            if(hover){fill=style==Style.PRIMARY?0xFF7CE3FF:0xCF245C78;edge=0xFF7BDFFF;}if(!active){fill=0x35162D3D;edge=0x664A6C80;}
            if(style==Style.SELECTED&&active)frame(g,x-2,y-2,w+4,h+4,0x1839C6FF,0x504BDAFF,4);
            frame(g,x,y,w,h,fill,edge,4);
            if(style==Style.PRIMARY||style==Style.SELECTED)g.horizontalLine(x+5,x+w-6,y+2,0x668CE9FF);
            int color=!active?0xFF8BA9BB:style==Style.PRIMARY?0xFF06273C:style==Style.DANGER?0xFFFFB6A8:TEXT;
            float size=style==Style.SELECT&&w<200?13:17;
            String label=clip(getMessage().getString(),w-(style==Style.SELECT?50:16),size);
            if(style==Style.SELECT){text(g,label,x+14,y+(h-(int)size)/2,size,color);chevron(g,x+w-30,y+h/2-3,color);}
            else centered(g,label,x+w/2,y+(h-17)/2,17,color);
        }
    }
    private static final class CoordinateBox extends EditBox {
        private final String label;
        CoordinateBox(int x,int y,int w,int h,String label){super(Minecraft.getInstance().font,x,y,w,h,Component.literal(label));this.label=label;setBordered(false);setMaxLength(10);}
        @Override public void onClick(MouseButtonEvent e,boolean doubleClick){setFocused(true);int n=Minecraft.getInstance().font.plainSubstrByWidth(getValue(),Math.max(0,(int)((e.x()-getX()-12)*9/17))).length();setCursorPosition(n);setHighlightPos(n);}
        @Override public void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
            frame(g,getX(),getY(),getWidth(),getHeight(),0xAA0B2130,isFocused()?0xFF78D5F5:0xFF4E7085,3);
            String value=getValue();text(g,value.isEmpty()?label:value,getX()+12,getY()+12,17,value.isEmpty()?MUTED:TEXT);
            if(isFocused()){
                int cursor=getX()+12+textWidth(value.substring(0,Math.min(getCursorPosition(),value.length())),17);
                if(!getHighlighted().isEmpty()){int sw=textWidth(getHighlighted(),17);int sx=getCursorPosition()+getHighlighted().length()<=value.length()&&value.substring(getCursorPosition()).startsWith(getHighlighted())?cursor:cursor-sw;g.fill(sx,getY()+9,sx+sw,getY()+31,0x5543B7E9);}
                if(System.currentTimeMillis()/500%2==0)g.fill(cursor,getY()+10,cursor+1,getY()+30,0xFFBCEAFF);
            }
        }
    }
}


