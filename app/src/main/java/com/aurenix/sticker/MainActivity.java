package com.aurenix.sticker;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.*;
import android.graphics.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.*;
import android.widget.Toast;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.transformer.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

@UnstableApi
public class MainActivity extends Activity {
 private static final String HOME="https://appassets.androidplatform.net/assets/index.html";
 private WebView web;
 private ValueCallback<Uri[]> picker;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private Transformer transformer;
 private boolean converting=false;
 private static final int PICK=71, PACK=72;
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  getWindow().setStatusBarColor(0xff000000); getWindow().setNavigationBarColor(0xff000000);
  web=new WebView(this); web.setBackgroundColor(0xff000000); setContentView(web);
  WebSettings settings=web.getSettings();
  settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
  settings.setAllowFileAccess(false); settings.setAllowContentAccess(true);
  settings.setMediaPlaybackRequiresUserGesture(false); settings.setJavaScriptCanOpenWindowsAutomatically(false);
  settings.setBlockNetworkLoads(true);
  web.addJavascriptInterface(new Bridge(),"AurenixAndroid");
  web.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest req){
    if(HOME.equals(req.getUrl().toString()))try{
     return new WebResourceResponse("text/html","UTF-8",getAssets().open("index.html"));
    }catch(IOException e){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream("Editor indisponivel".getBytes()));}
    return null;
   }
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){return !HOME.equals(req.getUrl().toString());}
  });
  web.setWebChromeClient(new WebChromeClient(){
   @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
    if(picker!=null)picker.onReceiveValue(null);picker=callback;
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
    String[] accept=params.getAcceptTypes(); ArrayList<String> types=new ArrayList<>();
    if(accept!=null)for(String entry:accept)if(entry!=null)for(String t:entry.split(","))if(t.trim().contains("/"))types.add(t.trim());
    if(types.size()==1)i.setType(types.get(0));else {i.setType("*/*");if(!types.isEmpty())i.putExtra(Intent.EXTRA_MIME_TYPES,types.toArray(new String[0]));}
    try{startActivityForResult(i,PICK);}catch(Exception e){picker.onReceiveValue(null);picker=null;notice("Não foi possível abrir os arquivos.");}
    return true;
   }
  });
  web.loadUrl(HOME);
 }
 @Override protected void onActivityResult(int req,int result,Intent data){
  super.onActivityResult(req,result,data);
  if(req==PICK&&picker!=null){picker.onReceiveValue(result==RESULT_OK&&data!=null&&data.getData()!=null?new Uri[]{data.getData()}:null);picker=null;}
  if(req==PACK){String error=data==null?null:data.getStringExtra("validation_error");notice(result==RESULT_OK?"Pacote aceito pelo WhatsApp.":error!=null?"WhatsApp: "+error:"Importação cancelada ou recusada.");}
 }
 private void notice(String msg){runOnUiThread(()->Toast.makeText(this,msg,Toast.LENGTH_LONG).show());}
 private static String filename(String name){String s=name.replaceAll("[/\\\\\\p{Cntrl}]","_").trim();return s.isEmpty()?"Aurenix Sticker":s.length()>140?s.substring(0,140):s;}
 private static byte[] decode(String data){int comma=data.indexOf(',');if(comma<0||data.length()>140_000_000)throw new IllegalArgumentException("Arquivo grande demais ou inválido.");return android.util.Base64.decode(data.substring(comma+1),android.util.Base64.DEFAULT);}
 private static void write(File file,byte[] bytes)throws IOException{try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}}
 private Uri store(File file,String name,String mime)throws IOException{
  boolean video=mime.startsWith("video/");
  Uri collection=video?MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY):MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
  ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,filename(name));values.put(MediaStore.MediaColumns.MIME_TYPE,mime);values.put(MediaStore.MediaColumns.RELATIVE_PATH,(video?Environment.DIRECTORY_MOVIES:Environment.DIRECTORY_DOWNLOADS)+"/Aurenix Sticker");values.put(MediaStore.MediaColumns.IS_PENDING,1);
  Uri uri=getContentResolver().insert(collection,values);if(uri==null)throw new IOException("Não foi possível criar o arquivo");
  try{
   try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Não foi possível gravar");byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);}
   ContentValues done=new ContentValues();done.put(MediaStore.MediaColumns.IS_PENDING,0);getContentResolver().update(uri,done,null,null);return uri;
  }catch(Exception e){getContentResolver().delete(uri,null,null);throw new IOException(e);}
 }
 private void publish(File file,String name,String mime,boolean share){
  worker.execute(()->{try{
   Uri uri=store(file,name,mime);notice(mime.startsWith("video/")?"Vídeo salvo na galeria: Movies/Aurenix Sticker":"Arquivo salvo em Downloads/Aurenix Sticker");
   if(share)runOnUiThread(()->{
    Intent i=new Intent(Intent.ACTION_SEND);i.setType(mime);i.putExtra(Intent.EXTRA_STREAM,uri);i.setClipData(ClipData.newRawUri("Aurenix Sticker",uri));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    try{startActivity(Intent.createChooser(i,"Compartilhar arquivo (não figurinha)"));}catch(Exception e){notice("Compartilhamento indisponível; arquivo salvo.");}
   });
  }catch(Exception e){notice("Erro ao salvar: "+e.getMessage());}finally{file.delete();}});
 }
 private void mp4(File input,String name,boolean share){
  runOnUiThread(()->{
   if(converting){input.delete();notice("Aguarde a conversão atual.");return;}
   converting=true;
   File output=new File(getCacheDir(),UUID.randomUUID()+".mp4");
   String outName=name.replaceFirst("(?i)\\.[^.]+$","")+".mp4";
   notice("Convertendo para MP4 (H.264/AAC)...");
   try{
    transformer=new Transformer.Builder(this).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).addListener(new Transformer.Listener(){
     @Override public void onCompleted(Composition composition,ExportResult result){converting=false;transformer=null;input.delete();publish(output,outName,"video/mp4",share);}
     @Override public void onError(Composition composition,ExportResult result,ExportException error){converting=false;transformer=null;output.delete();notice("Falha no MP4; salvando vídeo original. "+error.getMessage());publish(input,name,"video/webm",share);}
    }).build();
    transformer.start(MediaItem.fromUri(Uri.fromFile(input)),output.getAbsolutePath());
   }catch(Exception e){converting=false;transformer=null;output.delete();notice("Conversão indisponível. Salvando WebM.");publish(input,name,"video/webm",share);}
  });
 }
 public class Bridge {
  @JavascriptInterface public void save(String name,String mime,String data,boolean share){
   worker.execute(()->{File f=null;try{
    String type=mime.split(";")[0].toLowerCase(Locale.ROOT);
    if(!Arrays.asList("video/mp4","video/webm","image/webp","audio/wav","audio/x-wav","application/zip","application/json","application/octet-stream","").contains(type))type="application/octet-stream";
    if(type.isEmpty())type="application/octet-stream";
    String ext=type.equals("video/webm")?".webm":type.equals("video/mp4")?".mp4":".bin";
    f=File.createTempFile("aurenix",ext,getCacheDir());write(f,decode(data));
    if(type.equals("video/webm"))mp4(f,name,share);else publish(f,name,type,share);
   }catch(Exception e){if(f!=null)f.delete();notice("Erro ao exportar: "+e.getMessage());}});
  }
  @JavascriptInterface public void addPack(String json,String target){
   worker.execute(()->{File dir=null;try{
    JSONObject p=new JSONObject(json);JSONArray files=p.getJSONArray("items");boolean animated=p.getBoolean("animated");
    if(files.length()<3||files.length()>30)throw new IllegalArgumentException("Selecione de 3 a 30 figurinhas.");
    String id=UUID.randomUUID().toString();dir=new File(new File(getFilesDir(),"packs"),id);if(!dir.mkdirs())throw new IOException("Falha na pasta do pacote");
    JSONArray names=new JSONArray();
    for(int n=0;n<files.length();n++){
     JSONObject item=files.getJSONObject(n);byte[] bytes=decode(item.getString("data"));
     if(bytes.length>(animated?500000:100000))throw new IllegalArgumentException("Uma figurinha excede o limite do WhatsApp.");
     BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
     if(opts.outWidth!=512||opts.outHeight!=512)throw new IllegalArgumentException("Figurinha fora de 512x512.");
     write(new File(dir,"s"+n+".webp"),bytes);names.put(item.getString("name"));
    }
    Bitmap icon=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(icon);c.drawColor(Color.BLACK);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(0xff39ff14);paint.setTextSize(68);paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextAlign(Paint.Align.CENTER);c.drawText("A",48,72,paint);try(FileOutputStream out=new FileOutputStream(new File(dir,"tray.png"))){icon.compress(Bitmap.CompressFormat.PNG,100,out);}icon.recycle();
    String packName=p.getString("name");JSONObject meta=new JSONObject();meta.put("name",packName);meta.put("animated",animated);meta.put("items",names);write(new File(dir,"metadata.json"),meta.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    runOnUiThread(()->{
     Intent i=new Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK");i.setPackage("business".equals(target)?"com.whatsapp.w4b":"com.whatsapp");
     i.putExtra("sticker_pack_id",id);i.putExtra("sticker_pack_authority",PackProvider.AUTHORITY);i.putExtra("sticker_pack_name",packName);
     try{startActivityForResult(i,PACK);}catch(Exception e){notice("WhatsApp escolhido não encontrado ou não aceita pacotes.");}
    });
   }catch(Exception e){if(dir!=null){File[] children=dir.listFiles();if(children!=null)for(File f:children)f.delete();dir.delete();}notice("Pacote não criado: "+e.getMessage());}});
  }
 }
}
