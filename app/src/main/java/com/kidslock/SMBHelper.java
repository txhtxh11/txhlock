package com.kidslock;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.Map;
import java.util.TreeMap;

import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;

/**
 * 局域网 SMB 题库同步（jcifs-ng）。
 * 把 Windows 共享目录下的 *.txt 拉取到应用私有缓存目录，
 * 供 QuizLoader 读取。通过"远端清单指纹"检测文件增改，变化即自动重拉。
 */
public class SMBHelper {
    private static final String TAG = "SMBHelper";
    private static final String CACHE_DIR = "smb_quiz";

    /**
     * 同步一次。synchronized 保证不会与前一次未完成的同步并发。
     * 返回：>0 本次更新文件数；0 无变化/有同步在跑；-1 失败/未配置。
     */
    public static synchronized int syncOnce(Context ctx, Prefs prefs) {
        if (!prefs.smbEnabled()) return 0;
        String host = prefs.smbHost().trim();
        String share = prefs.smbShare().trim();
        if (host.isEmpty() || share.isEmpty()) return 0;

        File cacheDir = new File(ctx.getFilesDir(), CACHE_DIR);
        if (!cacheDir.exists()) cacheDir.mkdirs();

        try {
            String base = buildBaseUrl(prefs, host, share);
            SmbFile dir = new SmbFile(base);
            SmbFile[] files = dir.listFiles();
            if (files == null) return 0;

            // 远端 txt 清单：name -> size|mtime
            Map<String, String> remote = new TreeMap<>();
            for (SmbFile f : files) {
                if (f.isDirectory()) continue;
                String name = f.getName();
                if (!name.toLowerCase().endsWith(".txt")) continue;
                remote.put(name, f.length() + "|" + f.getLastModified());
            }

            String manifest = manifestString(remote);
            String oldManifest = prefs.smbManifest();
            if (manifest.equals(oldManifest)) return 0;   // 无变化

            int changed = 0, failed = 0;
            for (String name : remote.keySet()) {
                File out = new File(cacheDir, name);
                try {
                    SmbFile sf = new SmbFile(base + name);
                    try (InputStream in = new SmbFileInputStream(sf);
                         FileOutputStream fos = new FileOutputStream(out)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                    }
                    changed++;
                    Log.i(TAG, "已拉取: " + name);
                } catch (Exception e) {
                    failed++;
                    Log.w(TAG, "下载失败: " + name, e);
                }
            }
            // 清理远端已删除的本地缓存
            File[] cached = cacheDir.listFiles();
            if (cached != null) {
                for (File c : cached) {
                    if (!remote.containsKey(c.getName())) c.delete();
                }
            }
            // 有失败则不更新清单：保留旧清单，下次同步会重新下载失败文件
            if (failed == 0) {
                prefs.setSmbManifest(manifest);
            } else {
                Log.w(TAG, failed + " 个文件下载失败，保留原清单待重试");
            }
            return changed;
        } catch (Exception e) {
            Log.w(TAG, "SMB 同步失败: " + e.getMessage());
            return -1;
        }
    }

    private static String buildBaseUrl(Prefs prefs, String host, String share) throws Exception {
        StringBuilder url = new StringBuilder("smb://");
        String user = prefs.smbUser();
        if (user != null && !user.isEmpty()) {
            url.append(URLEncoder.encode(user, "UTF-8"))
               .append(':').append(URLEncoder.encode(prefs.smbPass(), "UTF-8"))
               .append('@');
        }
        url.append(host).append('/').append(URLEncoder.encode(share, "UTF-8"));
        String path = prefs.smbPath().trim();
        if (!path.isEmpty() && !"/".equals(path)) {
            for (String seg : path.split("/")) {
                if (!seg.isEmpty()) url.append('/').append(URLEncoder.encode(seg, "UTF-8"));
            }
        }
        String base = url.toString();
        return base.endsWith("/") ? base : base + "/";
    }

    private static String manifestString(Map<String, String> m) {
        StringBuilder sb = new StringBuilder();
        for (String k : new TreeMap<>(m).keySet()) {
            sb.append(k).append('=').append(m.get(k)).append(';');
        }
        return sb.toString();
    }

    /** SMB 拉取的本地缓存目录，供 QuizLoader 读取。 */
    public static File cacheDir(Context ctx) {
        return new File(ctx.getFilesDir(), CACHE_DIR);
    }
}
