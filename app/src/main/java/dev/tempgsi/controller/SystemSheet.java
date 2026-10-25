package dev.tempgsi.controller;
import android.app.*;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;

/** Content scrolls; primary and cancel actions remain reachable. */
final class SystemSheet {
    static void show(Activity activity,String eyebrow,String title,java.util.function.Consumer<LinearLayout> content,String action,Runnable accepted){
        Dialog dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=new LinearLayout(activity);root.setOrientation(1);
        int pad=Ui.dp(activity,24);root.setPadding(pad,Ui.dp(activity,14),pad,Ui.dp(activity,16));
        android.graphics.drawable.GradientDrawable surface=Ui.shape(activity,Ui.BG,32);surface.setStroke(Ui.dp(activity,1),0x30ffffff);root.setBackground(surface);
        LinearLayout handleRow=new LinearLayout(activity);handleRow.setGravity(Gravity.CENTER);
        View handle=new View(activity);handle.setBackground(Ui.shape(activity,0xff626878,4));handleRow.addView(handle,new LinearLayout.LayoutParams(Ui.dp(activity,36),Ui.dp(activity,4)));root.addView(handleRow);
        Ui.space(root,20);TextView small=Ui.text(root,eyebrow,11,Ui.ACCENT);small.setLetterSpacing(.12f);
        Ui.space(root,5);Ui.text(root,title,25,Ui.TEXT).setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));Ui.space(root,16);
        ScrollView scroll=new ScrollView(activity);scroll.setVerticalScrollBarEnabled(false);scroll.setClipToPadding(false);
        LinearLayout body=new LinearLayout(activity);body.setOrientation(1);content.accept(body);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Ui.button(root,action,true,()->{dialog.dismiss();accepted.run();});Ui.button(root,"Отмена",false,dialog::dismiss);
        dialog.setContentView(root);dialog.setCanceledOnTouchOutside(true);
        Window window=dialog.getWindow();
        if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams attributes=window.getAttributes();attributes.gravity=Gravity.BOTTOM;attributes.dimAmount=.65f;window.setAttributes(attributes);window.setNavigationBarColor(Ui.BG);window.setWindowAnimations(activity.getResources().getIdentifier("GsiSheetAnimation","style",activity.getPackageName()));}
        dialog.setOnShowListener(v->{if(window!=null)window.setLayout(-1,(int)(activity.getResources().getDisplayMetrics().heightPixels*.86f));Ui.reveal(body);});dialog.show();
    }
    private SystemSheet(){}
}
