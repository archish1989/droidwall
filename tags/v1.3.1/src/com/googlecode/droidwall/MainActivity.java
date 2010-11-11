/**
 * Main application activity.
 * This is the screen displayed when you open the application
 * 
 * Copyright (C) 2009  Rodrigo Zechin Rosauro
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Rodrigo Zechin Rosauro
 * @version 1.0
 */

package com.googlecode.droidwall;

import java.util.Arrays;
import java.util.Comparator;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.googlecode.droidwall.Api.DroidApp;

/**
 * Main application activity.
 * This is the screen displayed when you open the application
 */
public class MainActivity extends Activity implements OnCheckedChangeListener, OnClickListener {
	
	// Menu options
	private static final int MENU_SHOWRULES	= 1;
	private static final int MENU_APPLY		= 2;
	private static final int MENU_PURGE		= 3;
	private static final int MENU_SETPWD	= 4;
	private static final int MENU_HELP		= 5;
	
	/** progress dialog instance */
	private ProgressDialog progress = null;
	/** have we alerted about incompatible apps already? */
	private boolean alerted = false;
	private ListView listview;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkPreferences();
        if (savedInstanceState != null) {
        	alerted = savedInstanceState.getBoolean("alerted", false);
        }
		setContentView(R.layout.main);
		this.findViewById(R.id.label_interfaces).setOnClickListener(this);
		this.findViewById(R.id.img_3g).setOnClickListener(this);
		this.findViewById(R.id.img_wifi).setOnClickListener(this);
		this.findViewById(R.id.label_mode).setOnClickListener(this);
    }
    @Override
    protected void onResume() {
    	super.onResume();
    	if (this.listview == null) {
    		this.listview = (ListView) this.findViewById(R.id.listview);
    	}
    	refreshHeader();
		final String pwd = getSharedPreferences(Api.PREFS_NAME, 0).getString(Api.PREF_PASSWORD, "");
		if (pwd.length() == 0) {
			// No password lock
			showOrLoadApplications();
		} else {
			// Check the password
			requestPassword(pwd);
		}
    }
    @Override
    protected void onPause() {
    	super.onPause();
    	this.listview.setAdapter(null);
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
    	if (alerted) outState.putBoolean("alerted", true);
    }
    /**
     * Check if the stored preferences are OK
     */
    private void checkPreferences() {
    	final SharedPreferences prefs = getSharedPreferences(Api.PREFS_NAME, 0);
    	final Editor editor = prefs.edit();
    	boolean changed = false;
    	if (prefs.getString(Api.PREF_ITFS, "").length() == 0) {
    		editor.putString(Api.PREF_ITFS, Api.ITF_3G);
    		changed = true;
    	}
    	if (prefs.getString(Api.PREF_MODE, "").length() == 0) {
    		editor.putString(Api.PREF_MODE, Api.MODE_WHITELIST);
    		changed = true;
    	}
    	if (changed) editor.commit();
    }
    /**
     * Refresh informative header
     */
    private void refreshHeader() {
    	final SharedPreferences prefs = getSharedPreferences(Api.PREFS_NAME, 0);
    	final String itfs = prefs.getString(Api.PREF_ITFS, Api.ITF_3G);
    	final String mode = prefs.getString(Api.PREF_MODE, Api.MODE_WHITELIST);
		this.findViewById(R.id.img_wifi).setVisibility(itfs.indexOf(Api.ITF_WIFI) != -1 ? View.VISIBLE: View.INVISIBLE);
		this.findViewById(R.id.img_3g).setVisibility(itfs.indexOf(Api.ITF_3G) != -1 ? View.VISIBLE: View.INVISIBLE);
		final TextView labelmode = (TextView) this.findViewById(R.id.label_mode);
		if (mode.equals(Api.MODE_WHITELIST)) {
			labelmode.setText("Mode: White list (allow selected)");
		} else {
			labelmode.setText("Mode: Black list (block selected)");
		}
    }
    /**
     * Displays a dialog box to select which interfaces should be blocked
     */
    private void selectInterfaces() {
    	new AlertDialog.Builder(this).setItems(new String[]{"2G/3G Network","Wi-fi","Both"}, new DialogInterface.OnClickListener(){
			public void onClick(DialogInterface dialog, int which) {
				final String itfs = (which==0 ? Api.ITF_3G : which==1 ? Api.ITF_WIFI : Api.ITF_3G+"|"+Api.ITF_WIFI);
				final Editor editor = getSharedPreferences(Api.PREFS_NAME, 0).edit();
				editor.putString(Api.PREF_ITFS, itfs);
				editor.commit();
				refreshHeader();
			}
    	}).setTitle("Select interfaces:")
    	.show();
    }
    /**
     * Displays a dialog box to select the operation mode (black or white list)
     */
    private void selectMode() {
    	new AlertDialog.Builder(this).setItems(new String[]{"White list (allow selected)","Black list (block selected)"}, new DialogInterface.OnClickListener(){
			public void onClick(DialogInterface dialog, int which) {
				final String mode = (which==0 ? Api.MODE_WHITELIST : Api.MODE_BLACKLIST);
				final Editor editor = getSharedPreferences(Api.PREFS_NAME, 0).edit();
				editor.putString(Api.PREF_MODE, mode);
				editor.commit();
				refreshHeader();
			}
    	}).setTitle("Select mode:")
    	.show();
    }
    /**
     * Check and alert for incompatible apps
     */
    private void checkIncompatibleApps() {
        if (!alerted && Api.hastether != null) {
        	Api.alert(this, "Droid Wall has detected that you have the \"" + Api.hastether + "\" application installed on your system.\n\n" +
        		"Since this application also uses iptables, it will overwrite Droid Wall rules (and vice-versa).\n" +
        		"Please make sure that you re-apply Droid Wall rules every time you use \"" + Api.hastether + "\".");
        	alerted = true;
        }
    }
    /**
     * Set a new password lock
     * @param pwd new password (empty to remove the lock)
     */
	private void setPassword(String pwd) {
		final Editor editor = getSharedPreferences(Api.PREFS_NAME, 0).edit();
		editor.putString(Api.PREF_PASSWORD, pwd);
		String msg;
		if (editor.commit()) {
			if (pwd.length() > 0) {
				msg = "Password lock defined";
			} else {
				msg = "Password lock removed";
			}
		} else {
			msg = "Error changing password lock";
		}
		Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
	}
	/**
	 * Request the password lock before displayed the main screen.
	 */
	private void requestPassword(final String pwd) {
		new PassDialog(this, false, new android.os.Handler.Callback() {
			public boolean handleMessage(Message msg) {
				if (msg.obj == null) {
					MainActivity.this.finish();
					android.os.Process.killProcess(android.os.Process.myPid());
					return false;
				}
				if (!pwd.equals(msg.obj)) {
					requestPassword(pwd);
					return false;
				}
				// Password correct
				showOrLoadApplications();
				return false;
			}
		}).show();
	}
	/**
	 * If the applications are cached, just show them, otherwise load and show
	 */
	private void showOrLoadApplications() {
    	if (Api.applications == null) {
    		// The applications are not cached.. so lets display the progress dialog
    		progress = ProgressDialog.show(this, "Working...", "Reading installed applications", true);
        	final Handler handler = new Handler() {
        		public void handleMessage(Message msg) {
        			if (progress != null) progress.dismiss();
        			showApplications();
        		}
        	};
        	new Thread() {
        		public void run() {
        			Api.getApps(MainActivity.this);
        			handler.sendEmptyMessage(0);
        		}
        	}.start();
    	} else {
    		// the applications are cached, just show the list
        	showApplications();
    	}
	}
    /**
     * Show the list of applications
     */
    private void showApplications() {
        final DroidApp[] apps = Api.getApps(this);
        checkIncompatibleApps();
        // Sort applications - selected first, then alphabetically
        Arrays.sort(apps, new Comparator<DroidApp>() {
			@Override
			public int compare(DroidApp o1, DroidApp o2) {
				if (o1.selected == o2.selected) return o1.names[0].compareTo(o2.names[0]);
				if (o1.selected) return -1;
				return 1;
			}
        });
        final LayoutInflater inflater = getLayoutInflater();
		final ListAdapter adapter = new ArrayAdapter<DroidApp>(this,R.layout.listitem,R.id.itemtext,apps) {
        	@Override
        	public View getView(int position, View convertView, ViewGroup parent) {
       			ListEntry entry;
        		if (convertView == null) {
        			// Inflate a new view
        			convertView = inflater.inflate(R.layout.listitem, parent, false);
       				entry = new ListEntry();
       				entry.box = (CheckBox) convertView.findViewById(R.id.itemcheck);
       				entry.text = (TextView) convertView.findViewById(R.id.itemtext);
       				convertView.setTag(entry);
       				entry.box.setOnCheckedChangeListener(MainActivity.this);
        		} else {
        			// Convert an existing view
        			entry = (ListEntry) convertView.getTag();
        		}
        		final DroidApp app = apps[position];
        		entry.text.setText(app.toString());
        		final CheckBox box = entry.box;
        		box.setTag(app);
        		box.setChecked(app.selected);
       			return convertView;
        	}
        };
        this.listview.setAdapter(adapter);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	menu.add(0, MENU_SHOWRULES, 0, R.string.showrules).setIcon(R.drawable.show);
    	menu.add(0, MENU_APPLY, 0, R.string.applyrules).setIcon(R.drawable.apply);
    	menu.add(0, MENU_PURGE, 0, R.string.purgerules).setIcon(R.drawable.purge);
    	menu.add(0, MENU_SETPWD, 0, R.string.setpwd).setIcon(R.drawable.lock);
    	menu.add(0, MENU_HELP, 0, R.string.help).setIcon(android.R.drawable.ic_menu_help);
    	return true;
    }
    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
    	final Handler handler;
    	switch (item.getItemId()) {
    	case MENU_SHOWRULES:
    		progress = ProgressDialog.show(this, "Working...", "Please wait", true);
        	handler = new Handler() {
        		public void handleMessage(Message msg) {
        			if (progress != null) progress.dismiss();
        			if (!Api.hasRootAccess(MainActivity.this)) return;
           			Api.showIptablesRules(MainActivity.this);
        		}
        	};
			handler.sendEmptyMessageDelayed(0, 100);
    		return true;
    	case MENU_APPLY:
    		progress = ProgressDialog.show(this, "Working...", "Applying iptables rules.", true);
        	handler = new Handler() {
        		public void handleMessage(Message msg) {
        			if (progress != null) progress.dismiss();
        			if (!Api.hasRootAccess(MainActivity.this)) return;
        			if (Api.applyIptablesRules(MainActivity.this, true)) {
        				Toast.makeText(MainActivity.this, "Rules applied with success", Toast.LENGTH_SHORT).show();
        			}
        		}
        	};
			handler.sendEmptyMessageDelayed(0, 100);
    		return true;
    	case MENU_PURGE:
    		progress = ProgressDialog.show(this, "Working...", "Deleting iptables rules.", true);
        	handler = new Handler() {
        		public void handleMessage(Message msg) {
        			if (progress != null) progress.dismiss();
        			if (!Api.hasRootAccess(MainActivity.this)) return;
        			if (Api.purgeIptables(MainActivity.this)) {
        				Toast.makeText(MainActivity.this, "Rules purged with success", Toast.LENGTH_SHORT).show();
        			}
        		}
        	};
			handler.sendEmptyMessageDelayed(0, 100);
    		return true;
    	case MENU_SETPWD:
    		new PassDialog(this, true, new android.os.Handler.Callback() {
				public boolean handleMessage(Message msg) {
					if (msg.obj != null) {
						setPassword((String)msg.obj);
					}
					return false;
				}
    		}).show();
    		return true;
    	case MENU_HELP:
    		new HelpDialog(this).show();
    		return true;
    	}
    	return false;
    }
	/**
	 * Called an application is check/unchecked
	 */
	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		final DroidApp app = (DroidApp) buttonView.getTag();
		if (app != null) {
			app.selected = isChecked;
		}
	}
	
	private static class ListEntry {
		private CheckBox box;
		private TextView text;
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.label_interfaces:
		case R.id.img_3g:
		case R.id.img_wifi:
			selectInterfaces();
			break;
		case R.id.label_mode:
			selectMode();
			break;
		}
	}
}