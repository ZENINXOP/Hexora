package io.github.abdurazaaqmohammed.utils;

import android.graphics.Typeface;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.appcompat.app.AlertDialog;
import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.domain.files.ShellSession;
import io.github.abdurazaaqmohammed.domain.files.ShellWords;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public final class ScriptConsole {
    private ScriptConsole() { }
    public static void show(MainActivity context,File readable,File original,boolean root,boolean shizuku,List<String> arguments,long timeout) {
        String remote="/data/local/tmp/hexora-run-"+java.util.UUID.randomUUID()+".sh";
        boolean stageRemote=shizuku && original.getAbsolutePath().startsWith(context.getApplicationInfo().dataDir+"/");
        ShellSession.Factory factory=new ShellSession.Factory() {
            private Process launch(String command) throws Exception {
                if (shizuku) return new rikka.shizuku.ShizukuProcessFactory().newProcess(new String[]{"sh","-c",command},null,"/");
                if (root) return new ProcessBuilder(RootManager.getInstance(context).suBinary(),"-c",command).start();
                return new ProcessBuilder("/system/bin/sh","-c",command).start();
            }
            @Override public Process start(String marker) throws Exception {
                File script=root || shizuku ? original : readable;
                String directory=original.getParent();
                if (stageRemote) {
                    Process staging=launch("umask 077; cat > "+ShellWords.quote(remote));
                    try (InputStream input=new FileInputStream(readable); OutputStream output=staging.getOutputStream()) {
                        byte[] buffer=new byte[65536]; int count;
                        while ((count=input.read(buffer))!=-1) output.write(buffer,0,count);
                    }
                    if (staging.waitFor()!=0) throw new java.io.IOException("Cannot stage script for Shizuku.");
                    script=new File(remote); directory="/data/local/tmp";
                } else if (!root && !shizuku && (directory==null || !new File(directory).canRead())) directory=readable.getParent();
                StringBuilder command=new StringBuilder("cd "+ShellWords.quote(directory)+" && exec sh "+ShellWords.quote(script.getAbsolutePath()));
                for (String argument:arguments) command.append(' ').append(ShellWords.quote(argument));
                String bootstrap="printf '%s%s\\n' "+ShellWords.quote(marker)+" \"$$\" >&2; "+command;
                return launch("exec setsid sh -c "+ShellWords.quote(bootstrap));
            }
            @Override public void killGroup(long pid) throws Exception {
                Process kill=launch("kill -KILL -- -"+pid); kill.waitFor();
            }
            @Override public void cleanup() {
                if (stageRemote) try { Process remove=launch("rm -f "+ShellWords.quote(remote)); remove.waitFor(); } catch (Exception ignored) { }
            }
        };
        ShellSession session=new ShellSession(factory,timeout);
        LinearLayout body=new LinearLayout(context); body.setOrientation(LinearLayout.VERTICAL);
        int pad=Math.round(12*context.getResources().getDisplayMetrics().density); body.setPadding(pad,pad,pad,pad);
        TextView status=new TextView(context); status.setText(R.string.sh_running); body.addView(status);
        TextView output=new TextView(context); output.setTypeface(Typeface.MONOSPACE); output.setTextIsSelectable(true); output.setTextSize(12);
        ScrollView scroll=new ScrollView(context); scroll.addView(output);
        body.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(Math.round(context.getResources().getDisplayMetrics().heightPixels*.3f),Math.round(140*context.getResources().getDisplayMetrics().density))));
        EditText input=new EditText(context); input.setSingleLine(true); input.setHint(R.string.sh_input_hint);
        body.addView(input,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout buttons=new LinearLayout(context);
        MaterialButton send=new MaterialButton(context); send.setText(R.string.sh_send);
        MaterialButton eof=new MaterialButton(context); eof.setText(R.string.sh_end_input);
        buttons.addView(send); buttons.addView(eof); body.addView(buttons);
        ScrollView consoleScroll=new ScrollView(context); consoleScroll.addView(body);
        AlertDialog dialog=new MaterialAlertDialogBuilder(context).setTitle(R.string.sh_output).setView(consoleScroll)
                .setNegativeButton(R.string.sh_stop,null).setPositiveButton(R.string.close,null).create();
        send.setOnClickListener(v -> { try { session.send(input.getText().toString()); input.setText(""); } catch (Exception e) { input.setError(e.getMessage()); } });
        eof.setOnClickListener(v -> { try { session.endInput(); input.setEnabled(false); send.setEnabled(false); eof.setEnabled(false); } catch (Exception e) { input.setError(e.getMessage()); } });
        final Runnable[] tick=new Runnable[1]; final String[] previous={""};
        tick[0]=() -> {
            if (!dialog.isShowing()) return;
            String contents="STDERR:\n"+session.stderr.snapshot()+"\nSTDOUT:\n"+session.stdout.snapshot();
            if (!contents.equals(previous[0])) { output.setText(contents); previous[0]=contents; scroll.post(() -> scroll.fullScroll(android.view.View.FOCUS_DOWN)); }
            if (session.finished) {
                status.setText(session.timedOut ? "Command timed out" : session.wasStopped() ? "Stopped" : context.getString(R.string.sh_exit_code,session.exitCode));
                input.setEnabled(false); send.setEnabled(false); eof.setEnabled(false); dialog.getButton(-2).setEnabled(false);
            } else context.handler.postDelayed(tick[0],100);
        };
        dialog.setOnDismissListener(d -> { context.handler.removeCallbacks(tick[0]); if (!session.finished) session.stop(); });
        dialog.setOnShowListener(d -> {
            dialog.getButton(-2).setOnClickListener(v -> session.stop()); session.start(); context.handler.post(tick[0]);
        }); dialog.show();
    }
}
