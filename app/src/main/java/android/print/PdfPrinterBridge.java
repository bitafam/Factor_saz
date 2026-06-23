package android.print;

import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PrintDocumentAdapter;
import android.print.PrintAttributes;
import android.print.PageRange;
import android.print.PrintDocumentInfo;

public class PdfPrinterBridge {

    public interface PdfResultCallback {
        void onWriteFinished();
        void onWriteFailed(String error);
    }

    public static void printToPdf(final PrintDocumentAdapter adapter, final PrintAttributes attributes, final ParcelFileDescriptor pfd, final PdfResultCallback callback) {
        adapter.onLayout(null, attributes, null, new PrintDocumentAdapter.LayoutResultCallback() {
            @Override
            public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                adapter.onWrite(new PageRange[]{PageRange.ALL_PAGES}, pfd, null, new PrintDocumentAdapter.WriteResultCallback() {
                    @Override
                    public void onWriteFinished(PageRange[] pages) {
                        callback.onWriteFinished();
                    }

                    @Override
                    public void onWriteFailed(CharSequence error) {
                        callback.onWriteFailed(error != null ? error.toString() : "Unknown write error");
                    }

                    @Override
                    public void onWriteCancelled() {
                        callback.onWriteFailed("Write cancelled");
                    }
                });
            }

            @Override
            public void onLayoutFailed(CharSequence error) {
                callback.onWriteFailed(error != null ? error.toString() : "Unknown layout error");
            }

            @Override
            public void onLayoutCancelled() {
                callback.onWriteFailed("Layout cancelled");
            }
        }, null);
    }
}
