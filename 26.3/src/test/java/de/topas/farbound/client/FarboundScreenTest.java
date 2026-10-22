package de.topas.farbound.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.topas.farbound.FarboundNetwork;
import de.topas.farbound.FarboundUiLayout;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.InputType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Runs the actual screen and widgets with no window, world, server or game loop. */
class FarboundScreenTest {
    @TempDir Path root;
    MockedStatic<Minecraft> minecraft;
    MockedStatic<FabricLoader> fabric;
    Minecraft client;
    FarboundScreen screen;
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach void setup() throws Exception {
        client=mock(Minecraft.class,RETURNS_DEEP_STUBS);
        var font=mock(Font.class);
        when(font.plainSubstrByWidth(anyString(),anyInt())).thenAnswer(c -> c.getArgument(0));
        when(font.plainSubstrByWidth(anyString(),anyInt(),anyBoolean())).thenAnswer(c -> c.getArgument(0));
        var field=Minecraft.class.getDeclaredField("font");field.setAccessible(true);field.set(client,font);
        var gui=Minecraft.class.getDeclaredField("gui");gui.setAccessible(true);gui.set(client,mock(gui.getType(),RETURNS_DEEP_STUBS));
        when(client.getLastInputType()).thenReturn(InputType.MOUSE);
        minecraft=mockStatic(Minecraft.class);minecraft.when(Minecraft::getInstance).thenReturn(client);
        var loader=mock(FabricLoader.class);when(loader.getConfigDir()).thenReturn(root);
        fabric=mockStatic(FabricLoader.class);fabric.when(FabricLoader::getInstance).thenReturn(loader);
        FarboundClient.view=new FarboundNetwork.View();
        FarboundClient.view.status="DRAFT";FarboundClient.view.leader=true;FarboundClient.view.distance=10000;
        screen=new FarboundScreen();screen.width=854;screen.height=480;screen.init();
    }
    @AfterEach void cleanup() { fabric.close();minecraft.close(); }
    private void notice() { FarboundClient.view.notice="The player must be online.";screen.update(); }
    private boolean has(String label) { return screen.children().stream().anyMatch(c -> c instanceof AbstractWidget w && w.getMessage().getString().equals(label)); }
    private AbstractWidget control(String label){return screen.children().stream().filter(c->c instanceof AbstractWidget w&&w.getMessage().getString().equals(label)).map(c->(AbstractWidget)c).findFirst().orElseThrow();}
    private void clickControl(String label){var w=control(label);click(w.getX()+w.getWidth()/2d,w.getY()+w.getHeight()/2d);}
    private void click(double x,double y) {
        var layout=FarboundUiLayout.fit(screen.width,screen.height);
        screen.mouseClicked(new MouseButtonEvent(layout.screenX(x),layout.screenY(y),new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0)),false);
    }
    private void key(int scan,int code) { screen.keyPressed(new KeyEvent(scan,code,0)); }
    @Test void gotItDismissesNoticeWithMinecraftLeftClick() {
        notice();assertTrue(has("Got it"));click(716,545);assertFalse(has("Got it"));
        screen.update();assertFalse(has("Got it"),"Periodic state refresh must not reopen an acknowledged notice");
    }
    @Test void escapeDismissesNoticeThenClosesDashboard() {
        notice();key(InputConstants.KEY_ESCAPE,27);assertFalse(has("Got it"));
        key(InputConstants.KEY_ESCAPE,27);verify(client.gui).setScreen(null);
    }
    @Test void enterAndSpaceDismissNotice() {
        notice();key(InputConstants.KEY_RETURN,13);assertFalse(has("Got it"));
        FarboundClient.view.notice="A different error.";screen.update();
        key(InputConstants.KEY_SPACE,32);assertFalse(has("Got it"));
    }
    @Test void aNewActionCanReportTheSameErrorAgainWithoutPeriodicReopening() {
        notice();click(716,545);screen.update();assertFalse(has("Got it"));
        try(var actions=mockStatic(FarboundClient.class,CALLS_REAL_METHODS)) {
            actions.when(()->FarboundClient.action(anyString(),anyString())).thenAnswer(c->{FarboundClient.view.notice="";return null;});
            click(400,220);click(400,300); // A fresh configuration request clears the old acknowledgement.
            FarboundClient.view.notice="The player must be online.";screen.update();
            assertTrue(has("Got it"),"A new failed action must be explained even if its text matches the acknowledged error");
        }
    }
    @Test void confirmationBackAndEscapeReturnToDashboard() {
        click(850,740);assertTrue(has("Back"));click(716,545);assertFalse(has("Back"));
        click(850,740);assertTrue(has("Back"));key(InputConstants.KEY_ESCAPE,27);assertFalse(has("Back"));
    }
    @Test void dropdownCanBeDismissedWithEscape() throws Exception {
        click(400,220);var field=FarboundScreen.class.getDeclaredField("dropdown");field.setAccessible(true);
        assertEquals("distance",field.get(screen));key(InputConstants.KEY_ESCAPE,27);assertNull(field.get(screen));
    }
    private List<String> tooltip(int x,int y) throws Exception {
        var texts=new ArrayList<String>();var g=mock(GuiGraphicsExtractor.class);
        when(g.pose()).thenReturn(new org.joml.Matrix3x2fStack(32));
        doAnswer(c -> { texts.add(c.getArgument(1));return null; }).when(g).text(eq(client.font),anyString(),anyInt(),anyInt(),anyInt(),anyBoolean());
        var render=FarboundScreen.class.getDeclaredMethod("renderHelp",GuiGraphicsExtractor.class,int.class,int.class);render.setAccessible(true);
        render.invoke(screen,g,x,y);return texts;
    }
    @Test void homeRuleExplainsActualRadiusAndReferenceTooltipsStaySeparate() throws Exception {
        assertTrue(String.join(" ",tooltip(300,165)).contains("100 horizontal blocks"));
        assertNotEquals(tooltip(300,270),tooltip(500,270));
        assertEquals("Require travelling home",tooltip(300,165).getFirst());
    }
    @Test void inventoryMenuExplainsEachHoveredChoice() throws Exception {
        click(400,390);
        assertTrue(String.join(" ",tooltip(400,430)).contains("original inventory"));
        assertTrue(String.join(" ",tooltip(400,470)).contains("rollback"));
        assertNotEquals(tooltip(400,430),tooltip(400,470));
        assertTrue(tooltip(60,160).isEmpty(),"An open menu must not leak background tooltips");
    }
    @Test void dropdownSelectionWorksWithClickAndKeyboard() {
        try(var actions=mockStatic(FarboundClient.class,CALLS_REAL_METHODS)) {
            actions.when(()->FarboundClient.action(anyString(),anyString())).thenAnswer(c->null);
            click(400,220);click(400,300);
            actions.verify(()->FarboundClient.action("configure","distance 50000"));
            click(400,220);key(InputConstants.KEY_DOWN,1073741905);key(InputConstants.KEY_DOWN,1073741905);
            key(InputConstants.KEY_NUMPADENTER,1073741912);
            actions.verify(()->FarboundClient.action("configure","distance 100000"));
        }
    }
    @Test void summaryUsesLatestModeDistanceAndPersonalInventory() throws Exception {
        var method=FarboundScreen.class.getDeclaredMethod("summary");method.setAccessible(true);
        assertEquals("Home travel optional  ·  10,000–11,000 blocks  ·  Home gear stored",method.invoke(screen));
        var next=new FarboundNetwork.View();next.status="DRAFT";next.homeward=true;next.distance=50000;next.inventory="KEEP";
        FarboundClient.view=next;screen.update();
        assertEquals("Home travel required  ·  50,000–55,000 blocks  ·  Gear travels with you",method.invoke(screen));
        next.inventory="EMPTY";next.distance=1000000;
        assertEquals("Home travel required  ·  1,000,000–1,100,000 blocks  ·  Empty inventory required",method.invoke(screen));
    }
    @ParameterizedTest @ValueSource(strings={"leave","unready","end","abort","vote"})
    void confirmationButtonsDispatchOnlyTheReviewedAction(String action) {
        var v=FarboundClient.view;
        if(Set.of("end","abort","vote").contains(action)){v.status="ACTIVE";v.phase="ACTIVE";}
        if(action.equals("vote")){v.proposedNether=true;v.proposedEnd=false;}
        if(action.equals("unready"))v.ready=true;
        screen.update();
        try(var actions=mockStatic(FarboundClient.class,CALLS_REAL_METHODS)) {
            actions.when(()->FarboundClient.action(anyString(),anyString())).thenAnswer(c->null);
            if(action.equals("leave"))click(300,740);
            else if(action.equals("vote"))clickControl("Review rule change");
            else if(action.equals("end"))clickControl("Finish expedition");
            else if(action.equals("abort"))clickControl("Emergency return");
            else click(500,740);
            assertTrue(has("Back"));actions.verifyNoInteractions();
            click(350,545);
            actions.verify(()->FarboundClient.action(action.equals("unready")?"ready":action,""));
            assertFalse(has("Back"));
        }
    }
    @Test void reviewStartWaitsForServerReadinessBeforeLaunch() throws Exception {
        try(var actions=mockStatic(FarboundClient.class,CALLS_REAL_METHODS)) {
            actions.when(()->FarboundClient.action(anyString(),anyString())).thenAnswer(c->null);
            click(850,740);click(350,545);
            actions.verify(()->FarboundClient.action("ready",""));
            actions.verify(()->FarboundClient.action("launch",""),never());
            FarboundClient.view.ready=true;screen.update();
            var since=FarboundScreen.class.getDeclaredField("pendingSince");since.setAccessible(true);since.setLong(screen,System.currentTimeMillis()-200);
            screen.tick();actions.verify(()->FarboundClient.action("launch",""));
        }
    }
    @Test void modalBlocksBackgroundAndClosesAnAlreadyOpenMenu() {
        click(400,220);notice();
        try(var actions=mockStatic(FarboundClient.class,CALLS_REAL_METHODS)) {
            click(850,740);assertTrue(has("Got it"));actions.verifyNoInteractions();
            click(716,545);assertFalse(has("Got it"));
        }
    }
    @Test void tabCanReachConfirmationAndEnterCanSubmitIt() {
        try(var actions=mockStatic(FarboundClient.class,CALLS_REAL_METHODS)) {
            actions.when(()->FarboundClient.action(anyString(),anyString())).thenAnswer(c->null);
            click(300,740);key(InputConstants.KEY_TAB,9);
            assertEquals(212,((AbstractWidget)screen.getFocused()).getX());
            key(InputConstants.KEY_RETURN,13);actions.verify(()->FarboundClient.action("leave",""));
        }
    }
    @Test void friendsListInvitesOnlyAvailablePlayersAndPagesCorrectly() {
        var v=FarboundClient.view;var joined=UUID.randomUUID();
        v.people.add(new FarboundNetwork.Person(joined,"Leader",true,true,false,"HOME"));
        v.online.add(new FarboundNetwork.Online(joined,"Leader",false));v.online.add(new FarboundNetwork.Online(UUID.randomUUID(),"Pending",true));
        for(int i=0;i<5;i++)v.online.add(new FarboundNetwork.Online(UUID.randomUUID(),"Friend"+i,false));
        screen.update();click(900,165);assertTrue(has("Joined"));assertTrue(has("Invited"));
        try(var actions=mockStatic(FarboundClient.class,CALLS_REAL_METHODS)){
            actions.when(()->FarboundClient.action(anyString(),anyString())).thenAnswer(c->null);
            click(950,225);click(950,255);actions.verifyNoInteractions();
            click(950,290);actions.verify(()->FarboundClient.action("invite","Friend0"));
            click(900,417);click(950,225);actions.verify(()->FarboundClient.action("invite","Friend3"));
        }
    }
    @Test void inventoryHasTwoChoicesAndOpacityUsesWordsBelowActiveHeader() throws Exception {
        click(400,390);var options=FarboundScreen.class.getDeclaredField("options");options.setAccessible(true);
        assertEquals(2,((List<?>)options.get(screen)).size());key(InputConstants.KEY_ESCAPE,27);
        FarboundClient.view.status="ACTIVE";FarboundClient.view.phase="ACTIVE";FarboundClient.opacity=195;screen.update();
        assertTrue(has("Opacity: Medium"));assertFalse(has("Opacity 195"));
        click(900,570);assertEquals(3,((List<?>)options.get(screen)).size());click(900,600);
        assertEquals(135,FarboundClient.opacity);assertTrue(has("Opacity: Low"));
        for(var child:screen.children())if(child instanceof AbstractWidget w&&w.getY()==554)assertTrue(w.getY()+w.getHeight()<595,"Appearance controls must end above Elapsed");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"pause","resume","abort"})
    void profileActionsRequireConfirmationAndDispatchOnlyTheirOwnRequest(String action){
        var v=FarboundClient.view;v.status="ACTIVE";v.phase=action.equals("pause")?"ACTIVE":"PAUSED";v.canPause=true;screen.update();
        try(var actions=mockStatic(FarboundClient.class,CALLS_REAL_METHODS)){
            actions.when(()->FarboundClient.action(anyString(),anyString())).thenAnswer(c->null);
            clickControl(action.equals("pause")?"Pause & return":action.equals("resume")?"Resume expedition":"Abandon expedition");
            assertTrue(has("Back"));actions.verifyNoInteractions();click(350,545);
            actions.verify(()->FarboundClient.action(action,""));assertFalse(has("Back"));
        }
    }
    @Test void keepDisablesPauseAndOperatorRecoveryUsesTheSelectedIdentity(){
        var v=FarboundClient.view;v.status="ACTIVE";v.phase="ACTIVE";v.canPause=false;screen.update();
        try(var actions=mockStatic(FarboundClient.class,CALLS_REAL_METHODS)){
            actions.when(()->FarboundClient.action(anyString(),anyString())).thenAnswer(c->null);
            clickControl("Pause & return");assertFalse(has("Back"));actions.verifyNoInteractions();
            v.admin=true;var id=UUID.randomUUID();v.recovery.add(new FarboundNetwork.Online(id,"Friend",false));screen.update();
            click(100,60);click(400,415);assertTrue(has("Retry Friend"));click(400,350);
            actions.verify(()->FarboundClient.action("admin_retry",id.toString()));
        }
    }
    @Test void activeReturnActionsShareOneAreaAndLockedSettingsAreNotControls(){
        var v=FarboundClient.view;v.status="ACTIVE";v.phase="ACTIVE";v.canPause=true;screen.update();
        assertFalse(has("Store at home"));assertFalse(has("10,000 blocks"));assertFalse(has("My location"));assertFalse(has("Solo"));
        int previousBottom=330;
        for(String label:List.of("Finish expedition","Pause & return","Emergency return")){
            var w=control(label);assertEquals(654,w.getX());assertEquals(340,w.getWidth());
            assertTrue(w.getY()>=previousBottom+10);assertTrue(w.getY()+w.getHeight()<=492);previousBottom=w.getY()+w.getHeight();
        }
        var timer=screen.children().stream().filter(c->c instanceof AbstractWidget w&&w.getMessage().getString().startsWith("Timer:")).map(c->(AbstractWidget)c).findFirst().orElseThrow();
        var opacity=control("Opacity: "+(FarboundClient.opacity<165?"Low":FarboundClient.opacity<215?"Medium":"High"));
        assertEquals(timer.getY(),opacity.getY());assertEquals(timer.getHeight(),opacity.getHeight());
        assertTrue(timer.getY()+timer.getHeight()<=594);assertTrue(opacity.getX()>=timer.getX()+timer.getWidth()+12);
    }
    @Test void activePartyShowsExploringAndTimerHasItsOwnLine() throws Exception {
        var v=FarboundClient.view;v.status="ACTIVE";v.phase="ACTIVE";v.elapsedTicks=480;
        for(int i=0;i<8;i++)v.people.add(new FarboundNetwork.Person(UUID.randomUUID(),"Player"+i,true,i==0,true,"ACTIVE"));
        screen.update();when(client.getConnection()).thenReturn(null);
        var texts=new ArrayList<String>();var g=mock(GuiGraphicsExtractor.class);when(g.pose()).thenReturn(new org.joml.Matrix3x2fStack(32));
        doAnswer(c->{texts.add(c.getArgument(1));return null;}).when(g).text(eq(client.font),anyString(),anyInt(),anyInt(),anyInt(),anyBoolean());
        for(String method:List.of("labels","renderMembers")){
            var render=FarboundScreen.class.getDeclaredMethod(method,GuiGraphicsExtractor.class);render.setAccessible(true);render.invoke(screen,g);
        }
        assertEquals(8,texts.stream().filter("Exploring"::equals).count());assertFalse(texts.contains("Ready"));
        assertTrue(texts.contains("Elapsed: 00:00:24"));assertFalse(texts.stream().anyMatch(t->t.contains("Home travel optional")));
    }
}
