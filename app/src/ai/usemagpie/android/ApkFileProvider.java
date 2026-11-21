package ai.usemagpie.android;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;

/** Minimal content provider exposing downloaded update APKs to the system package installer. */
public class ApkFileProvider extends ContentProvider {
    public static final String AUTHORITY = "ai.usemagpie.android.fileprovider";

    private File fileFor(Uri uri) {
        String name = uri.getLastPathSegment();
        return new File(getContext().getCacheDir(), "updates/" + name);
    }

    @Override public boolean onCreate() { return true; }

    @Override public Cursor query(Uri uri, String[] proj, String sel, String[] selArgs, String sort) {
        File f = fileFor(uri);
        MatrixCursor c = new MatrixCursor(new String[]{"_display_name", "_size"});
        c.addRow(new Object[]{f.getName(), f.length()});
        return c;
    }

    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String sel, String[] selArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String sel, String[] selArgs) { return 0; }
}
