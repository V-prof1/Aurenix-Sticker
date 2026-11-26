package com.aurenix.sticker;

import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

public class PackProvider extends ContentProvider {
 public static final String AUTHORITY="com.aurenix.sticker.provider";
 private static final String[] META={"sticker_pack_identifier","sticker_pack_name","sticker_pack_publisher","sticker_pack_icon","android_play_store_link","ios_app_download_link","sticker_pack_publisher_email","sticker_pack_publisher_website","sticker_pack_privacy_policy_website","sticker_pack_license_agreement_website","image_data_version","whatsapp_will_not_cache_stickers","animated_sticker_pack"};
 private static final String[] STICKERS={"sticker_file_name","sticker_emoji","sticker_accessibility_text"};
 @Override public boolean onCreate(){return true;}
 private File root(){return new File(getContext().getFilesDir(),"packs");}
 private File dir(String id){if(!id.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Invalid ID");return new File(root(),id);}
 private JSONObject meta(File folder)throws Exception{try(InputStream in=new FileInputStream(new File(folder,"metadata.json"))){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);return new JSONObject(out.toString("UTF-8"));}}
 private void add(MatrixCursor cursor,File folder){try{JSONObject m=meta(folder);cursor.addRow(new Object[]{folder.getName(),m.getString("name"),"Aurenix Sticker","tray.png","","","","","","","1",0,m.getBoolean("animated")?1:0});}catch(Exception ignored){}}
 @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
  List<String> parts=uri.getPathSegments();
  if(parts.size()>=1&&parts.get(0).equals("metadata")){
   MatrixCursor c=new MatrixCursor(META);
   if(parts.size()==1){File[] dirs=root().listFiles();if(dirs!=null)for(File d:dirs)if(d.isDirectory())add(c,d);}else if(parts.size()==2)add(c,dir(parts.get(1)));else throw new IllegalArgumentException("Unknown URI");
   return c;
  }
  if(parts.size()==2&&parts.get(0).equals("stickers")){
   MatrixCursor c=new MatrixCursor(STICKERS);try{JSONArray a=meta(dir(parts.get(1))).getJSONArray("items");for(int n=0;n<a.length();n++)c.addRow(new Object[]{"s"+n+".webp","🙂",a.getString(n)});}catch(Exception ignored){}return c;
  }
  throw new IllegalArgumentException("Unknown URI");
 }
 @Override public AssetFileDescriptor openAssetFile(Uri uri,String mode)throws FileNotFoundException{
  List<String> p=uri.getPathSegments();if(!"r".equals(mode)||p.size()!=3||!p.get(0).equals("stickers_asset")||!p.get(2).matches("tray\\.png|s[0-9]{1,2}\\.webp"))throw new FileNotFoundException();
  File folder=dir(p.get(1));File f=new File(folder,p.get(2));if(!new File(folder,"metadata.json").isFile()||!f.isFile())throw new FileNotFoundException();
  return new AssetFileDescriptor(ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY),0,f.length());
 }
 @Override public String getType(Uri uri){List<String> p=uri.getPathSegments();if(p.size()==3&&p.get(0).equals("stickers_asset"))return p.get(2).endsWith(".png")?"image/png":"image/webp";if(p.size()>0&&p.get(0).equals("metadata"))return "vnd.android.cursor."+(p.size()==1?"dir":"item")+"/vnd."+AUTHORITY+".metadata";return "vnd.android.cursor.dir/vnd."+AUTHORITY+".stickers";}
 @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
 @Override public int update(Uri uri,ContentValues values,String s,String[] a){throw new UnsupportedOperationException();}
 @Override public int delete(Uri uri,String s,String[] a){throw new UnsupportedOperationException();}
}
