package mways.com.preference.network;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

public class ErrorDialogue {
    private Context context;
    private String errorMessage;
    private String title;

    public ErrorDialogue(Context context, String title, String errorMessage) {
        this.context = context;
        this.errorMessage = errorMessage;
        this.title = title;
        showErrorDialogue(errorMessage);
    }

    private void showErrorDialogue(String errorMessage) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(errorMessage);
        builder.setCancelable(false);
        String positiveText = context.getString(android.R.string.ok);
        builder.setPositiveButton(positiveText,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        AlertDialog dialog = builder.create();
        // Positive
        dialog.setCancelable(false);
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextSize(16);
    }
}
