package com.khizhny.smsbanking;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.Telephony.Sms;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.PopupMenu.OnMenuItemClickListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.khizhny.smsbanking.model.Bank;
import com.khizhny.smsbanking.model.Rule;
import com.khizhny.smsbanking.model.SubRule;
import com.khizhny.smsbanking.model.Transaction;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

import jxl.Cell;
import jxl.CellView;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.write.Label;
import jxl.write.Number;
import jxl.write.NumberFormat;
import jxl.write.WritableCellFormat;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import jxl.write.biff.RowsExceededException;
import xml.SmsBankingWidget;

import static com.khizhny.smsbanking.MyApplication.KEY_HIDE_MATCHED_MESSAGES;
import static com.khizhny.smsbanking.MyApplication.KEY_HIDE_NOT_MATCHED_MESSAGES;
import static com.khizhny.smsbanking.MyApplication.KEY_IGNORE_CLONES;
import static com.khizhny.smsbanking.MyApplication.LOG;
import static com.khizhny.smsbanking.MyApplication.db;
import static com.khizhny.smsbanking.MyApplication.forceRefresh;
import static com.khizhny.smsbanking.MyApplication.getExtraParameterNames;
import static com.khizhny.smsbanking.MyApplication.hideMatchedMessages;
import static com.khizhny.smsbanking.MyApplication.hideNotMatchedMessages;
import static com.khizhny.smsbanking.MyApplication.ignoreClones;

public class MainActivity extends AppCompatActivity implements OnMenuItemClickListener,
        OnItemClickListener, TimePickerDialog.OnTimeSetListener, DatePickerDialog.OnDateSetListener,
        SwipeRefreshLayout.OnRefreshListener{

		private static final String LIST_STATE = "listState";
    private static final int REQUEST_CODE_ASK_PERMISSIONS = 123;
		private static final int DEF_SMS_REQ_FOR_DEL = 888; // asking to be set as default app for msg delete.
		private static final int DEF_SMS_REQ_FOR_EDIT = 889; // asking to be set as default app for msg edit.
		private static final int DEF_SMS_REQ_FOR_TEST = 890; // asking to be set as default app for creating test messages.

		// Constants for creatins test messsages for stress testing
		private static final int MENU_ITEM_ITEM1 = 1;
		private static final int TEST_MSG_COUNT = 100;
		private static final int TEST_MSG_MINUTES_INTERVAL =2;
		private static final String TEST_SENDER = "TestSender";

    private static final String EXPORT_FOLDER = "SMS banking";

    public static final String KEY_TODO = "todo";
    public static final String KEY_TODO_ADD = "add";
    public static final String KEY_TODO_EDIT = "edit";
    public static final String KEY_RULE_ID = "rule_id";
    public static final String KEY_SMS_BODY = "sms_body";
		private static final String KEY_HIDE_CURRENCY = "hide_currency";
		private static final String KEY_UPDATE_AVAILABLE = "update_available";
		private static final String KEY_BANK_ID = "bank_id";
		private static final String URL_4PDA_PRIVACY = "http://4pda.ru/forum/index.php?showtopic=730676&st=20#entry58120636";
		private static final String KEY_COUNTRY_PREFERENCE = "country_preference";
		private static final String KEY_INVERSE_RATE = "inverse_rate";
		private static final String KEY_HIDE_ADS = "hide_ads";



		private ListView listView;
    private List<Transaction> transactions;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressDialog pDialog;
    private Transaction selectedTransaction;
    private TransactionListAdapter transactionListAdapter;
    private RuleListAdapter ruleListAdapter;
    private Boolean hideCurrency;
    private Boolean inverseRate;
    private Boolean hideAds;
    private String country;
    private List<Bank> myBanks;
    private Bank activeBank;

    private RefreshTransactionsTask refreshTransactionsTask;
    private int firstVisiblePosition = 0;
    private AlertDialog firstRunDialog;
    private AlertDialog pickRuleDialog;
    private AlertDialog pickCountryDialog;

    private static final Calendar calendar = new GregorianCalendar();

		public void createTestSMS(){
				// Make Lots of fake SMS
				Uri uri = Uri.parse("content://sms/inbox");
				ContentValues cv = new ContentValues();
				ContentResolver cr = getContentResolver();
				for (int i=1; i<TEST_MSG_COUNT; i++) {
						cv.put("address", TEST_SENDER);
						cv.put("date", String.valueOf(System.currentTimeMillis()-TEST_MSG_MINUTES_INTERVAL*60000*(TEST_MSG_COUNT-i)));
						cv.put("body", "Test message #"+i+" 1 UAH was spent. "+(TEST_MSG_COUNT-i)+" UAH left.");
						cv.put("type", 1);
						cv.put("thread_id", 4);
						cv.put("read", 1);
						cr.insert(uri, cv);
				}
				Bank testBank=new Bank();
				testBank.setActive(1);
				testBank.setName("Test Bank");
				testBank.setPhone(TEST_SENDER);
				testBank.setCountry(country);
				testBank.setDefaultCurrency("UAH");

				Rule r = new Rule(testBank, "rule#0");
				//						0		   1       2  3  4   5    6     7   8   9
				r.setSmsBody("Test message #666 1 UAH was spent. 456 UAH left.");
				r.setRuleType(Rule.transactionType.WITHDRAW.ordinal());
				Rule.makeInitialWordSplitting(r);
				r.words.get(2).changeWordType(); // 0
				r.words.get(3).changeWordType(); // 1
				r.words.get(4).changeWordType(); // 2
				r.words.get(7).changeWordType(); // 3
				SubRule srDiff = new SubRule(r,Transaction.Parameters.ACCOUNT_DIFFERENCE);
				srDiff.regexPhraseIndex=1;
				srDiff.negate=true;
				SubRule srCurr = new SubRule(r,Transaction.Parameters.CURRENCY);
				srCurr.regexPhraseIndex=2;
				SubRule srAfter = new SubRule(r,Transaction.Parameters.ACCOUNT_STATE_AFTER);
				srAfter.regexPhraseIndex=3;
				// making 30 more copies of the Rule
				for (int j=0; j<30; j++) {
						new Rule(r,testBank);
				}
				db.addOrEditBank(testBank,true,true);
				onRefresh();
		}

		protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(LOG, "MainActivity:onCreate()");

        if (getIntent().getBooleanExtra(KEY_UPDATE_AVAILABLE, false)) {
            goToMarket();
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            MyApplication.hasReadSmsPermission = (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED);
            if (!MyApplication.hasReadSmsPermission) {
                requestPermissions(new String[]{Manifest.permission.READ_SMS}, REQUEST_CODE_ASK_PERMISSIONS);
            }
        }

        setContentView(R.layout.activity_main);
        setTitle("");
        listView = findViewById(R.id.listView);
        listView.setOnItemClickListener(this);

        swipeRefreshLayout = findViewById(R.id.swipe_layout);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeResources(R.color.red, R.color.blue, R.color.green);
            swipeRefreshLayout.setOnRefreshListener(this);
        }


        // Checking if Activity was called by widget clicking.
        // If so, changing active bank to the bank linked with this widget.
        this.getIntent().getComponent();
        int widget_bank_id = this.getIntent().getIntExtra(KEY_BANK_ID, 0);
        if (widget_bank_id > 0) {
            Log.d(MyApplication.LOG, "Main Activity launched from widget click and bank_id=" + widget_bank_id);
            db.setActiveBank(widget_bank_id);
        }


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(LOG, "MainActivity:onCreateOptionsMenu()");
        getMenuInflater().inflate(R.menu.menu_main_activity, menu);
				if (BuildConfig.DEBUG) {
						menu.add(Menu.NONE, MENU_ITEM_ITEM1, Menu.NONE, "CreateTestMsg");
				}
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(LOG, "MainActivity:onOptionsItemSelected() user clicked "+item.getTitle());
        // Listener for Menu options
        Intent intent;
        switch (item.getItemId()) {
            case R.id.action_preferences:
                startActivity(new Intent(this, PrefActivity.class));
								return true;
            case R.id.action_bank_my_list:
                intent = new Intent(this, BankListActivity.class);
                intent.putExtra("bankFilter", "myBanks");
                startActivity(intent);
								return true;
            case R.id.action_rule_list:
                if (activeBank!=null){
                    if (activeBank.ruleList!=null) {
                        showRulePickerDialog(activeBank.ruleList, null);
                    }
                }
								return true;
            case R.id.action_statistics:
                intent = new Intent(this, StatisticsActivity.class);
                startActivity(intent);
								return true;
            case R.id.action_export_transactions:
                exportToExcel();
								return true;
            case R.id.action_rate_app:
                goToMarket();
								return true;
            case R.id.action_privacy:

                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(URL_4PDA_PRIVACY));
                startActivity(i);
								return true;
            case R.id.action_about:
                showAboutDialog();
								return true;
            case R.id.action_cache:
                if (activeBank!=null && transactions!=null) {
                    CacheTransactionsTask cacheTransactionsTask=new CacheTransactionsTask();
                    cacheTransactionsTask.execute(transactions);
                }else{
                    Toast.makeText(getApplicationContext(), R.string.nothing_to_cache, Toast.LENGTH_SHORT).show();
                }
								return true;
            case R.id.bank_clear_cache:
                if (activeBank!=null) {
                    db.deleteBankCache(activeBank.getId());
                    Toast.makeText(MainActivity.this, R.string.cache_deleted, Toast.LENGTH_SHORT).show();
                    onRefresh();
                }
                return true;
            case R.id.action_quit:
								/*createRestSMS();/**/
								this.finish();
                System.exit(0);
								return true;
						case MENU_ITEM_ITEM1:
							showMessageOKCancel("Add test SMS?",(dialog, which) -> {
												if (!isDefaultSmsApp()) {
														sendDefaultSmsAppRequest(DEF_SMS_REQ_FOR_TEST);
												} else {
														createTestSMS();
												}
										},null);
								return true;

            default:
                return false;
        }
    }

    // Listener for popup rule menu clicks
    public boolean onMenuItemClick(MenuItem item) {
        Log.d(LOG, "MainActivity:OnMenuItemClick() user clicked "+item.getTitle());
        Intent intent;
        switch (item.getItemId()) {

            // delete rule option
            case R.id.item_delete_rule:
                if (selectedTransaction.applicableRules.size()==1) {
                    db.deleteRule(selectedTransaction.applicableRules.get(0).getId());
                    onRefresh();
                } else {
                    // Creating dialog for rule picking
                    showRulePickerDialog(selectedTransaction.applicableRules,selectedTransaction);
                }
                return true;

            // edit rule option
            case R.id.item_edit_rule :
                if (selectedTransaction.applicableRules.size()==1) {
                    intent = new Intent(this, RuleActivity.class);
                    intent.putExtra(KEY_RULE_ID, selectedTransaction.applicableRules.get(0).getId());
                    intent.putExtra(KEY_TODO, KEY_TODO_EDIT);
                    startActivity(intent);
                } else {
                    // Creating dialog for rule edition
										// if t=null then click on the rule will triger editing
                    showRulePickerDialog(selectedTransaction.applicableRules,null);
                }
                return true;

            // switch rule option
            case R.id.item_switch_rule:
                if (selectedTransaction.applicableRules.size()==2){
                    selectedTransaction.switchToNextRule();
                    onRefresh();
                } else { // if 3 or more rules showing dialog to pick up the rule
                    showRulePickerDialog(selectedTransaction.applicableRules,selectedTransaction);
                }
                return true;

            // create new rule option
						case R.id.item_new_rule:
								showCreateNewRuleDialog(selectedTransaction.getSmsBody());
								return true;

						//editing date/Time option
						case R.id.item_edit_sms:
								if (!isDefaultSmsApp()) {
										intent = new Intent(Sms.Intents.ACTION_CHANGE_DEFAULT);
										intent.putExtra(Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
												startActivityForResult(intent, DEF_SMS_REQ_FOR_EDIT);
								} else {
										showDatePickerDialog();
								}
								return true;

						//deleting SMS option
						case R.id.item_delete_sms:
								if (!isDefaultSmsApp()) {
                        intent = new Intent(Sms.Intents.ACTION_CHANGE_DEFAULT);
                        intent.putExtra(Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
												startActivityForResult(intent, DEF_SMS_REQ_FOR_DEL);
                }else{
                		showDeleteSmsDialog();
								}
                return true;
        }
        return false;
    }

    @Override
    protected void onStop() {
        Log.d(LOG, "MainActivity:OnStop() started");
        if (firstRunDialog !=null){
            if (firstRunDialog.isShowing()) firstRunDialog.dismiss();
        }

        Log.d(LOG, "MainActivity:OnStop() finished");
        super.onStop();
    }

		@Override
		protected void onActivityResult(int requestCode, int resultCode, Intent data)
		{
				switch (requestCode)
				{
						case DEF_SMS_REQ_FOR_DEL:
								if (resultCode == Activity.RESULT_OK){
										showDeleteSmsDialog();
								}
								return;
						case DEF_SMS_REQ_FOR_EDIT:
								if (resultCode == Activity.RESULT_OK){
										showDatePickerDialog();
								}
						case DEF_SMS_REQ_FOR_TEST:
								if (resultCode == Activity.RESULT_OK){
										createTestSMS();
								}
								return;
				}
		}

		private void showDatePickerDialog(){
				calendar.setTime(selectedTransaction.getTransactionDate());
				int year = calendar.get(Calendar.YEAR);
				int month = calendar.get(Calendar.MONTH);  // Beware: months are zero-based and no out of range errors are reported
				int day = calendar.get(Calendar.DAY_OF_MONTH);
				new DatePickerDialog(this, this,year,month,day).show();
		}

    @Override
    protected void onResume() {
        Log.d(LOG, "MainActivity:OnResume()");


        // Restoring preferences
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        hideCurrency = settings.getBoolean(KEY_HIDE_CURRENCY, false);
        inverseRate = settings.getBoolean(KEY_INVERSE_RATE, false);
        hideAds = settings.getBoolean(KEY_HIDE_ADS, false);
        country = settings.getString(KEY_COUNTRY_PREFERENCE,null);


        if (country!=null) {
            loadMyBanks();
            if (transactions!=null) {
                transactionListAdapter = new TransactionListAdapter(transactions);
                listView.setAdapter(transactionListAdapter);
                if (firstVisiblePosition != 0) {
                    Log.d(LOG, "MainActivity:OnResume() ListState restored");
                    listView.setSelection(firstVisiblePosition);
                }
            } else {
                // reloading transactions to list
                forceRefresh=false;
                onRefresh();

            }
            if (forceRefresh) {
                forceRefresh=false;
                onRefresh();

            }
        } else {
            showCountryPickDialog();
        }

        // enabling ads banner
        AdView mAdView = findViewById(R.id.adView);
        if (!hideAds) {
            // real: ca-app-pub-1260562111804726/2944681295
            MobileAds.initialize(getApplicationContext(), getResources().getString(R.string.banner_ad_unit_id));
            AdRequest adRequest = new AdRequest.Builder().build();
            mAdView.loadAd(adRequest);
        } else {
            mAdView.setVisibility(View.GONE);
        }

        super.onResume();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        Log.d(LOG, "MainActivity:onSaveInstanceState() started");
        super.onSaveInstanceState(state);
        state.putInt(LIST_STATE,listView.getFirstVisiblePosition());
        Log.d(LOG, "MainActivity:onSaveInstanceState() finished");
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        Log.d(LOG, "MainActivity:onRestoreInstanceState() started");
        super.onRestoreInstanceState(state);
        if (!forceRefresh) {
						firstVisiblePosition = state.getInt(LIST_STATE);
            Log.d(LOG, "MainActivity instance restored...");
        }
        Log.d(LOG, "MainActivity:onRestoreInstanceState() finished");
    }


    @Override
    protected void onDestroy() {
        Log.d(LOG, "MainActivity:onDestroy()");
				refreshScreenWidgets();
        // restoring default SMS managing application
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (!MyApplication.defaultSmsApp.equals(getPackageName())) {
                Intent intent = new Intent(Sms.Intents.ACTION_CHANGE_DEFAULT);
                intent.putExtra(Sms.Intents.EXTRA_PACKAGE_NAME, MyApplication.defaultSmsApp);
                startActivity(intent);
            }
        }
        if (pDialog!=null) pDialog.dismiss();
        if (refreshTransactionsTask!=null) refreshTransactionsTask.cancel(false);
        super.onDestroy();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[]  permissions, @NonNull int[] grantResults) {
        Log.d(LOG, "MainActivity:onRequestPermissionsResult()");
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    MyApplication.hasReadSmsPermission = true;
                    onRefresh();
                } else {
                    // Permission Denied
                    Toast.makeText(MainActivity.this, "READ_SMS permision denied", Toast.LENGTH_SHORT)
                            .show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void goToMarket(){
        Log.d(LOG, "MainActivity:goToMarket()");
        Uri uri = Uri.parse("market://details?id=" + getPackageName());
        Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);
        // To count with Play market backstack, After pressing back button,
        // to taken back to our application, we need to add following flags to intent.
        goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        try {
            startActivity(goToMarket);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("http://play.google.com/store/apps/details?id=" + getPackageName())));
        }
    }

    private void refreshScreenWidgets() {
        Log.d(LOG, "MainActivity:refreshScreenWidgets()");
        //Refreshing onscreen widgets
        if (myBanks!=null) {
            if (myBanks.size() > 0) {
                ComponentName name = new ComponentName(MainActivity.this, SmsBankingWidget.class);
                int[] ids = AppWidgetManager.getInstance(MainActivity.this).getAppWidgetIds(name);
                Intent update = new Intent();
                update.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                update.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                Log.d(MyApplication.LOG, "Sending broadcast to MainActivity to refresh widgets...");
                MainActivity.this.sendBroadcast(update);
            }
        }
        Log.d(MyApplication.LOG, "UpdateMyAccountsState finished...");
    }

    /*
     *  Handler for item click in transaction list
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // Transaction list View popup menu listener
        Log.d(LOG, "MainActivity:onItemClick()");
        PopupMenu popupMenu = new PopupMenu(this, view);
        popupMenu.setOnMenuItemClickListener(this);
        selectedTransaction = transactionListAdapter.getItem(position);
        if (selectedTransaction!=null) {
            if (!selectedTransaction.hasCalculatedTransactionDate) { // show popup menu only for non calculated transactions
                popupMenu.inflate(R.menu.popup_menu_main);
                if (selectedTransaction.ruleOptionsCount() < 2) { // hiding switch rule option if not needed
                    popupMenu.getMenu().removeItem(R.id.item_switch_rule);
                }
                if (selectedTransaction.ruleOptionsCount() < 1) { // hiding delete rule option if not needed
                    popupMenu.getMenu().removeItem(R.id.item_delete_rule);
                }
                if (selectedTransaction.ruleOptionsCount() < 1) { // hiding edit rule option if not needed
                    popupMenu.getMenu().removeItem(R.id.item_edit_rule);
                }
                popupMenu.show();
            }
        }
    }

    /**
     * Refreshes transaction list
     */
    @Override
    public void onRefresh() {
        Log.d(LOG, "MainActivity:onRefresh() started");
        activeBank = db.getActiveBank();
        if (activeBank != null) {

            refreshTransactionsTask = new RefreshTransactionsTask();
            refreshTransactionsTask.execute(activeBank);
        } else {
            swipeRefreshLayout.setRefreshing(false);
        }
        Log.d(LOG, "MainActivity:onRefresh() ended");
    }


    private boolean isDefaultSmsApp(){
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
						String defaultSmsApp = Sms.getDefaultSmsPackage(this);
						if (defaultSmsApp == null) {
								return false;
						} else {
								return defaultSmsApp.equals(getPackageName());
						}
				}else{
						return true;
				}

		}


		private class TransactionListAdapter extends ArrayAdapter<Transaction> {

        TransactionListAdapter(List<Transaction> transactions) {
            super(MainActivity.this, R.layout.activity_main_list_row, transactions);

        }

        @SuppressLint("DefaultLocale")
				@NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View rowView=null;
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                if (inflater != null) {
                    rowView = inflater.inflate(R.layout.activity_main_list_row, parent, false);
                }
            }else{
                rowView = convertView;
            }
            Transaction t = transactions.get(position);

            if (rowView!=null) {
                TextView smsTextView = rowView.findViewById(R.id.smsBody);
                smsTextView.setText(t.getSmsBody());

                // Warning sign
                TextView warnView = rowView.findViewById(R.id.warning_sign);
                warnView.setTextColor(Color.DKGRAY);
                if (t.isCached) {
                    warnView.setText("(c)");
                } else {
                    if (t.ruleOptionsCount() != 1) {
                        warnView.setText(String.format("(%d)", t.ruleOptionsCount()));
                        warnView.setTextColor(Color.RED);
                    } else {
                        warnView.setText("");
                    }
                }

                // Filling Before state
                TextView accountBeforeView = rowView.findViewById(R.id.stateBefore);
                if (t.hasStateBefore) {
                    accountBeforeView.setText(t.getStateBeforeAsString(hideCurrency));
                } else {
                    accountBeforeView.setText("");
                }
                // Filling Transaction Date
                TextView dateView = rowView.findViewById(R.id.transaction_date);
                dateView.setText(t.getTransactionDateAsString("dd.MM.yyyy HH:mm"));

                // Filling After state
                TextView accountAfterView = rowView.findViewById(R.id.stateAfter);
                if (t.hasStateAfter) {
                    accountAfterView.setText(t.getStateAfterAsString(hideCurrency));
                } else {
                    accountAfterView.setText("");
                }
                // Filling commission
                TextView accountCommissionView = rowView.findViewById(R.id.transactionCommission);
                if (t.getCommission().equals(new BigDecimal("0.00"))) {
                    accountCommissionView.setVisibility(View.GONE);
                } else {
                    accountCommissionView.setVisibility(View.VISIBLE);
                    accountCommissionView.setText(t.getCommissionAsString(hideCurrency, true));
                    accountCommissionView.setTextColor(Color.rgb(218, 48, 192)); //pink
                }
                // Filling difference
                TextView accountDifferenceView = rowView.findViewById(R.id.stateDifference);
                if (t.hasStateDifference) {
                    accountDifferenceView.setVisibility(View.VISIBLE);
                    accountDifferenceView.setText(t.getDifferenceAsString(hideCurrency, inverseRate, false));
                    switch (t.getStateDifference().signum()) {
                        case -1:
                            accountDifferenceView.setTextColor(Color.RED);
                            break;
                        case 0:
                            accountDifferenceView.setTextColor(Color.GRAY);
                            break;
                        case 1:
                            accountDifferenceView.setTextColor(Color.rgb(0, 100, 0)); // dark green
                    }
                } else {
                    //accountDifferenceView.setVisibility(View.GONE);
                    accountDifferenceView.setText("");
                }
                // Changing icon
                ImageView iconView = rowView.findViewById(R.id.transactionIcon);
                iconView.setImageResource(t.icon);

                // Filling Extra parameters
                if (t.hasExtras()) {
                    ((TextView) rowView.findViewById(R.id.extra1)).setText(t.getExtraParam1());
                    ((TextView) rowView.findViewById(R.id.extra2)).setText(t.getExtraParam2());
                    ((TextView) rowView.findViewById(R.id.extra3)).setText(t.getExtraParam3());
                    ((TextView) rowView.findViewById(R.id.extra4)).setText(t.getExtraParam4());
                    rowView.findViewById(R.id.extra1).setVisibility(View.VISIBLE);
                    rowView.findViewById(R.id.extra2).setVisibility(View.VISIBLE);
                    rowView.findViewById(R.id.extra3).setVisibility(View.VISIBLE);
                    rowView.findViewById(R.id.extra4).setVisibility(View.VISIBLE);
                } else {
                    rowView.findViewById(R.id.extra1).setVisibility(View.GONE);
                    rowView.findViewById(R.id.extra2).setVisibility(View.GONE);
                    rowView.findViewById(R.id.extra3).setVisibility(View.GONE);
                    rowView.findViewById(R.id.extra4).setVisibility(View.GONE);
                }
                return rowView;
            }else{
                return new View(MainActivity.this);
            }
        }

    }


    private class RuleListAdapter extends ArrayAdapter<Rule> {
        private Rule selectedRule;
        private final Transaction selectedTransaction;

        RuleListAdapter(List<Rule> ruleList, Transaction t) {
            super(MainActivity.this,R.layout.activity_rule_list_row, ruleList);
            selectedTransaction=t;
        }

        //Handler for rule picker dialog
        @NonNull
        @Override
        public View getView(int position, View rowView , @NonNull ViewGroup parent) {
            if (rowView == null) {
                LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                if (vi != null) {
                    rowView = vi.inflate(R.layout.activity_rule_list_row, parent, false);
                }
            }

            if (rowView != null) {
                TextView ruleNameView = rowView.findViewById(R.id.ruleName);
                ImageView ruleIcon = rowView.findViewById(R.id.ruleIcon);
                Rule r = ruleListAdapter.getItem(position);
                ruleNameView.setText("---");
                if (r != null) {
                    ruleNameView.setText(r.getName());
										ruleIcon.setImageResource(r.getRuleIconId());
                    rowView.setTag(r);
                }

                // switching rule
                ruleNameView.setOnClickListener(v -> {
										if (selectedTransaction != null) {
												selectedRule = (Rule) ((View) v.getParent()).getTag();
												selectedTransaction.switchToRule(selectedRule);
												onRefresh();
												pickRuleDialog.dismiss();
										}else{
												selectedRule = (Rule) ((View) v.getParent()).getTag();
												pickRuleDialog.dismiss();
												Intent intent = new Intent(MainActivity.this, RuleActivity.class);
												intent.putExtra(KEY_RULE_ID, selectedRule.getId());
												intent.putExtra(KEY_TODO, KEY_TODO_EDIT);
												startActivity(intent);
												forceRefresh = true;
												pickRuleDialog.dismiss();
										}
								});

                ImageButton vDeleteRule = rowView.findViewById(R.id.delete_rule_button);
                vDeleteRule.setOnClickListener(v -> {
										selectedRule = (Rule) ((View) v.getParent()).getTag();
										db.deleteRule(selectedRule.getId());
										onRefresh();
										pickRuleDialog.dismiss();
								});

            }
            //noinspection ConstantConditions
            return rowView;
        }

    }

    private void exportToExcel() {
        Log.d(LOG, "MainActivity:exportToExcel() started");
        //Saving in external storage
        File sdCard = Environment.getExternalStorageDirectory();
        File directory = new File(sdCard.getAbsolutePath() + "/" + EXPORT_FOLDER);

        //create directory if not exist
        if (!directory.isDirectory()) {
            if (!directory.mkdirs()){
                Toast.makeText(this, "Can't create forder " + directory, Toast.LENGTH_SHORT).show();
            }
        }

        Bank bank = db.getActiveBank();

        //file path
        if (bank!=null) {
            String FILE_NAME = bank.getName() + ".xls";
            File file = new File(directory, FILE_NAME);

            WorkbookSettings wbSettings = new WorkbookSettings();
            wbSettings.setLocale(new Locale("en", "EN"));
            WritableWorkbook workbook;

            try {
                workbook = Workbook.createWorkbook(file, wbSettings);
                WritableSheet worksheet = workbook.createSheet(bank.getName(), 0);

                try {
                    int i = 0;
                    String extraParameterNames[]= getExtraParameterNames(this);
                    worksheet.addCell(new Label(0, i, "N"));
                    worksheet.addCell(new Label(1, i, "TransactionDate"));
                    worksheet.addCell(new Label(2, i, "State before"));
                    worksheet.addCell(new Label(3, i, "Difference"));
                    worksheet.addCell(new Label(4, i, "DifferenceInNativeCurrency"));
                    worksheet.addCell(new Label(5, i, "Rate"));
                    worksheet.addCell(new Label(6, i, "Commission"));
                    worksheet.addCell(new Label(7, i, "StateAfter"));
                    worksheet.addCell(new Label(8, i, "TransactionCurrency"));
                    worksheet.addCell(new Label(9, i,  !extraParameterNames[0].equals("")? extraParameterNames[0]:"ExtraParam1"));
                    worksheet.addCell(new Label(10, i, !extraParameterNames[1].equals("")? extraParameterNames[1]:"ExtraParam2"));
                    worksheet.addCell(new Label(11, i, !extraParameterNames[2].equals("")? extraParameterNames[2]:"ExtraParam3"));
                    worksheet.addCell(new Label(12, i, !extraParameterNames[3].equals("")? extraParameterNames[3]:"ExtraParam4"));
                    worksheet.addCell(new Label(13, i, "TransactionType"));
                    worksheet.addCell(new Label(14, i, "SMS"));

                    for (Transaction t : transactions) {
                        i = i + 1;
                        worksheet.addCell(new Label(0, i, i + ""));
                        worksheet.addCell(new Label(1, i, t.getTransactionDateAsString("yyyy-MM-dd hh:mm:ss")));
                        if (t.hasStateBefore) addBigDecimal(worksheet, 2, i, t.getStateBefore(), 2);
                        if (t.hasStateDifference) {
                            addBigDecimal(worksheet, 3, i, t.getStateDifference(), 2);
                            addBigDecimal(worksheet, 4, i, t.getStateDifferenceInNativeCurrency(false), 2);
                            if (!(t.getCurrencyRate().equals(new BigDecimal("1.000"))))
                                addBigDecimal(worksheet, 5, i, t.getCurrencyRate(), 3);
                        }
                        if (!(t.getCommission().equals(new BigDecimal("0.00"))))
                            addBigDecimal(worksheet, 6, i, t.getCommission(), 2);
                        if (t.hasStateAfter) addBigDecimal(worksheet, 7, i, t.getStateAfter(), 2);
                        worksheet.addCell(new Label(8, i, t.getTransactionCurrency()));
                        worksheet.addCell(new Label(9, i, t.getExtraParam1()));
                        worksheet.addCell(new Label(10, i, t.getExtraParam2()));
                        worksheet.addCell(new Label(11, i, t.getExtraParam3()));
                        worksheet.addCell(new Label(12, i, t.getExtraParam4()));
                        worksheet.addCell(new Label(13, i, t.getTransactionType()));
                        worksheet.addCell(new Label(14, i, t.getSmsBody()));
                    }
                    sheetAutoFitColumns(worksheet);
                    workbook.write();
                    workbook.close();
                    Toast.makeText(this, "File saved to " + directory + "/" + FILE_NAME, Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
                    intent.setData(uri);
                    try {
                        startActivity(intent);
                    }catch (Exception e){
                        //Toast.makeText(this,"",Toast.LENGTH_LONG);
                    }
                } catch (RowsExceededException e) {
                    Toast.makeText(this, "Too many rows to save.", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                } catch (WriteException e) {
                    Toast.makeText(this, "Can't write to " + directory + "/" + FILE_NAME, Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
            } catch (IOException e) {
                Toast.makeText(this, "Can't write to " + directory + "/" + FILE_NAME, Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }else{
            Toast.makeText(this, R.string.go_to_banks, Toast.LENGTH_SHORT).show();

        }
        Log.d(LOG, "MainActivity:exportToExcel() finished");
    }

    private void sheetAutoFitColumns(WritableSheet sheet) {
        for (int i = 0; i < sheet.getColumns(); i++) {
            Cell[] cells = sheet.getColumn(i);
            int longestStrLen = -1;

            if (cells.length == 0)
                continue;

        /* Find the widest cell in the column. */
            for (Cell cell : cells) {
                if (cell.getContents().length() > longestStrLen) {
                    String str = cell.getContents();
                    if (str == null || str.isEmpty())
                        continue;
                    longestStrLen = str.trim().length();
                }
            }

        /* If not found, skip the column. */
            if (longestStrLen == -1)
                continue;

        /* If wider than the max width, crop width */
            if (longestStrLen > 255)
                longestStrLen = 255;

            CellView cv = sheet.getColumnView(i);
            cv.setSize(longestStrLen * 256 + 100); /* Every character is 256 units wide, so scale it. */
            sheet.setColumnView(i, cv);
        }
    }

    private void showFirstRunDialog() {
        Log.d(LOG, "MainActivity:showFirstRunDialog()");
        if (myBanks.size() == 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(getResources().getString(R.string.tip_bank_1));
            firstRunDialog = builder.create();
            firstRunDialog.show();
        }
    }

		private void sendDefaultSmsAppRequest(int rcCode){
				Intent intent = new Intent(Sms.Intents.ACTION_CHANGE_DEFAULT);
				intent.putExtra(Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
				startActivityForResult(intent, rcCode);
		}

		private void showDeleteSmsDialog(){
				try {
						Uri uriSms = Uri.parse("content://sms/inbox");
						Cursor c = getContentResolver().query(uriSms, new String[]{"_id", "thread_id", "address", "person", "date", "body"}, "_id=" + selectedTransaction.smsId, null, null);
						if (c != null) {
								if (c.moveToFirst()) {
										ContentValues values = new ContentValues();
										values.put("read", true);
										getContentResolver().update(Uri.parse("content://sms/"), values, "_id=" + c.getLong(0), null);
										getContentResolver().delete(Uri.parse("content://sms/" + c.getLong(0)), "date=?",new String[]{c.getString(4)});
								}
								c.close();
								// refreshing.
								transactions.remove(selectedTransaction);
								transactionListAdapter.notifyDataSetChanged();
						}
				} catch (Exception e) {
						Log.e("log>>>", e.toString());
				}
		}

		@Override
		public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
				calendar.set(year,month,dayOfMonth);
				Calendar tr_calendar=new GregorianCalendar();
				tr_calendar.setTime(selectedTransaction.getTransactionDate());
				int hours=tr_calendar.get(Calendar.HOUR_OF_DAY);
				int minutes =tr_calendar.get(Calendar.MINUTE);
				new TimePickerDialog(this, this, hours, minutes, true).show();
		}

		@Override
		public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
				int year=calendar.get(Calendar.YEAR);
				int month=calendar.get(Calendar.MONTH);
				int day=calendar.get(Calendar.DAY_OF_MONTH);
				calendar.set(year,month,day,hourOfDay,minute);
				try {
						Uri uriSms = Uri.parse("content://sms/inbox");
						Cursor c = getContentResolver().query(uriSms, new String[]{"_id", "thread_id", "address", "person", "date", "body"}, "_id=" + selectedTransaction.smsId, null, null);
						if (c != null) {
								if (c.moveToFirst()) {
										long smsId=c.getLong(0);
										ContentValues values = new ContentValues();
										values.put("read", true);
										long newDate = calendar.getTimeInMillis();
										values.put("date", String.valueOf(newDate));
										getContentResolver().update(uriSms, values, "_id=" + smsId, null);
								}
								c.close();
								onRefresh();
						}
				} catch (Exception e) {
						Log.e("log>>>", e.toString());
				}
		}

		private void showAboutDialog() {
        Log.d(LOG, "MainActivity:showAboutDialog() started");
        PackageInfo pInfo;
        String version="unknown";
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }


        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setTitle(getResources().getString(R.string.app_name)+" ("+version+")");
        builder.setMessage(R.string.about_text);
        builder.create().show();
        Log.d(LOG, "MainActivity:showAboutDialog() finished");
    }

    private void showCreateNewRuleDialog(String smsBody) {
        Log.d(LOG, "showCreateNewRuleDialog() started");
        if (smsBody != null) {
            Intent intent = new Intent(MainActivity.this, RuleActivity.class);
            intent.putExtra(KEY_SMS_BODY, smsBody);
            intent.putExtra(KEY_TODO, KEY_TODO_ADD);
            startActivity(intent);
        }
    }

    private void showCountryPickDialog(){
        Log.d(LOG, "MainActivity:showCountryPickDialog()");
        boolean flag=false;
        if (pickCountryDialog==null) {  // onRefresh is called twice for some reasons !!
            flag=true;
        }else {
            if (!pickCountryDialog.isShowing()) flag=true;
        }
        if (flag) {

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.pick_your_country);
            builder.setSingleChoiceItems(R.array.countries_array,-1, (dialog, which) -> {
								// Updating country settings in preferences
								Log.d(LOG, "MainActivity:showCountryPickDialog().onClick");
								SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
								country = getResources().getStringArray(R.array.countries_array)[which];
								settings.edit().putString(KEY_COUNTRY_PREFERENCE, country).apply();
								db.open();
								db.setDefaultCountry(country);  // update db with selected country
								loadMyBanks();
								dialog.dismiss();
								showFirstRunDialog();
						});
            pickCountryDialog = builder.create();
            pickCountryDialog.show();
        }
    }

    private void addBigDecimal(WritableSheet sheet, int column, int row, BigDecimal value, int digits) throws WriteException {
        NumberFormat numberFormat;
        if (digits==3) {
            numberFormat = new NumberFormat("0.000");
        }else{
            numberFormat = new NumberFormat("0.00");
        }
        WritableCellFormat cellFormat = new WritableCellFormat(numberFormat);
        Double v = round(value.doubleValue(),digits);
        Number number = new Number(column, row, v, cellFormat);
        sheet.addCell(number);
    }

    private static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

    /**
     * Task loads a list of transactions from SMS using rules defined for BankV2
     * BankV2
     */
     private class RefreshTransactionsTask extends AsyncTask<Bank, Integer, List<Transaction>> {

        private static final long CACHE_NOTIFY_THRESHOLD = 5000; // in milliseconds

				private long refreshTime;

        @Override
        protected void onPreExecute() {
            Log.d(LOG,"RefreshTransactionsTask preExecuted. (hideMatchedMessages="+hideMatchedMessages);
            refreshTime= System.currentTimeMillis();
            super.onPreExecute();
            swipeRefreshLayout.setRefreshing(true);
            if (pDialog != null) pDialog.dismiss();
						// Save the ListView state
						firstVisiblePosition = listView.getFirstVisiblePosition();
            pDialog = new ProgressDialog(MainActivity.this);
            pDialog.setMessage(getString(R.string.reading_messages));
            pDialog.setCancelable(false);
            pDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            pDialog.setMax(100);
            pDialog.setProgress(1);
            pDialog.show();

            // Restoring preferences
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            hideCurrency = settings.getBoolean(KEY_HIDE_CURRENCY, false);
            inverseRate = settings.getBoolean(KEY_INVERSE_RATE, false);
            hideAds = settings.getBoolean(KEY_HIDE_ADS, false);
            hideMatchedMessages = settings.getBoolean(KEY_HIDE_MATCHED_MESSAGES, false);
            hideNotMatchedMessages = settings.getBoolean(KEY_HIDE_NOT_MATCHED_MESSAGES, false);
            ignoreClones = settings.getBoolean(KEY_IGNORE_CLONES, false);
        }

        @Override
        protected List<Transaction> doInBackground(Bank... params) {
            Log.d(LOG,"RefreshTransactionsTask.doInBackground() started");
            Bank activeBank = params[0];
            if (activeBank==null) return null;

            // Loading transactions from cache.
            List<Transaction> transactionList;
            Date lastCachedTransactionDate;
            transactionList = db.getTransactionCache(activeBank.getId());
            if (transactionList.size()>0) {
                lastCachedTransactionDate = transactionList.get(0).getTransactionDate();
            }else{
                lastCachedTransactionDate=new Date(0);
            }

            // Loading transactions from SMS.
            String smsBody;
            String prevSmsBody="";
            String phoneNumbers = activeBank.getPhone().replace(";", "','");
            Cursor c;
            if (MyApplication.hasReadSmsPermission) {
                Uri uri = Uri.parse("content://sms/inbox");
                c = getContentResolver().query(uri, null, "address IN ('" + phoneNumbers + "') and date>"+lastCachedTransactionDate.getTime(), null, "date DESC");
            } else {
                return transactionList;
            }
            if (c != null) {
                int msgCount = c.getCount();
								pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                pDialog.setMax(msgCount-1);
                if (c.moveToFirst()) {
                    for (int ii = 0; ii < msgCount; ii++) {
                        if (!isCancelled()) {
                            publishProgress(ii + 1);
                        } else{
                            if (pDialog.isShowing()) pDialog.dismiss();
                            return null;
                        }
                        boolean skipMessage=false;
                        Date transactionDate = new Date(c.getLong(c.getColumnIndexOrThrow("date")));
                        if (!transactionDate.after(lastCachedTransactionDate)) skipMessage=true;

                        smsBody = Transaction.removeBadChars(c.getString(c.getColumnIndexOrThrow("body")));

                        if (ignoreClones && smsBody.equals(prevSmsBody)) skipMessage=true;

                        if (!skipMessage) {
                            // if sms body is duplicating previous one and ignoreClones flag is set - just skip message
                            Transaction transaction = new Transaction(smsBody,activeBank.getDefaultCurrency(),transactionDate);
                            transaction.smsId = c.getLong(c.getColumnIndexOrThrow("_id"));
                            Boolean messageHasIgnoreTypeRule = false;

                            for (Rule rule : activeBank.ruleList) {
                                if (smsBody.matches(rule.getMask())) {
                                    if (rule.hasIgnoreType()) {
                                        messageHasIgnoreTypeRule = true;
                                    } else {
                                        transaction.applicableRules.add(rule);
                                    }
                                }
                            }
                            boolean transactionMatched = transaction.applicableRules.size()>0;

                             if (transactionMatched) {
                                 if (hideMatchedMessages) skipMessage=true;
                             }else{ // not matched
                                 if (hideNotMatchedMessages) skipMessage=true;
                             }

                             if (!skipMessage && !messageHasIgnoreTypeRule) {
                                 transaction.applyBestRule();
                                 transaction.calculateMissedData();
                                 transactionList.add(transaction);
                             }

                        }
                        prevSmsBody=smsBody;
                        c.moveToNext();
                    }
                }
                c.close();
            }
            transactionList = Transaction.addMissingTransactions(transactionList);

            // saving last bank account state to db for later usage
            if (transactionList.size()>0) {
                activeBank.setCurrentAccountState(transactionList.get(0).getStateAfter());
                db.addOrEditBank(activeBank,false,false);
            }
            Log.d(LOG,"RefreshTransactionsTask.doInBackground() finished");
            return transactionList;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            pDialog.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(List<Transaction> t) {
            super.onPostExecute(t);
            refreshTime = System.currentTimeMillis()-refreshTime;
            Log.d(LOG,"RefreshTransactionsTask.onPostExecute() started");
            // Dismiss the progress dialog
            if (pDialog.isShowing())
                pDialog.dismiss();

            if (t!=null) {
                transactions=t;
                transactionListAdapter = new TransactionListAdapter(t);
                listView.setAdapter(transactionListAdapter);
                if (refreshTime>CACHE_NOTIFY_THRESHOLD) {
                    Toast.makeText(getApplicationContext(), R.string.cache_needed,Toast.LENGTH_LONG).show();
                }
            }
            if (transactionListAdapter!=null) {
                transactionListAdapter.notifyDataSetChanged();
            }
            swipeRefreshLayout.setRefreshing(false);
            // restoring position if possible
            try {
                if (firstVisiblePosition != 0) {
                    Log.d(LOG, "MainActivity:onPostExecute() list restored after refreshing");
                    listView.setSelection(firstVisiblePosition);
                }
            } catch (Exception e) {
                Log.d(LOG, "MainActivity:onPostExecute() failed to restore");
            }
            Log.d(LOG,"RefreshTransactionsTask.onPostExecute() finished");
        }
    }

    private class CacheTransactionsTask extends AsyncTask<List <Transaction>, Integer, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Log.d(LOG,"CacheTransactionsTask.onPreExecute() started");
            swipeRefreshLayout.setRefreshing(true);
            if (pDialog != null) pDialog.dismiss();
            pDialog = new ProgressDialog(MainActivity.this);
            pDialog.setMessage(MessageFormat.format("{0}...", getString(R.string.caching)));
            pDialog.setCancelable(false);
            pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            pDialog.setMax(transactions.size());
            pDialog.setProgress(0);
            pDialog.show();
            // Restoring preferences
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            hideCurrency = settings.getBoolean(KEY_HIDE_CURRENCY, false);
            inverseRate = settings.getBoolean(KEY_INVERSE_RATE, false);
            hideAds = settings.getBoolean(KEY_HIDE_ADS, false);
        }

				@SafeVarargs
				@Override
        protected final Void doInBackground(List<Transaction>... params) {
            Log.d(LOG,"CacheTransactionsTask.doInBackground() started");
            db.deleteBankCache(activeBank.getId());
            int progress=0;
            for (Transaction t:transactions) {
                ContentValues cv = t.getContentValues();
                cv.put(KEY_BANK_ID,activeBank.getId());
                db.cacheTransaction(cv);
                publishProgress(progress++);
            }
            Log.d(LOG,"CacheTransactionsTask.doInBackground() finished");
           return null;
        }

        @Override
        protected void onProgressUpdate(Integer ... values) {
            super.onProgressUpdate(values);
            pDialog.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Log.d(LOG,"CacheTransactionsTask.onPostExecute() started");
            // Dismiss the progress dialog
            if (pDialog.isShowing())
                pDialog.dismiss();
            swipeRefreshLayout.setRefreshing(false);
            // restoring position if possible
            try {
                if (firstVisiblePosition != 0){
                    listView.setSelection(firstVisiblePosition);
                    Log.d(LOG, "MainActivity:onPostExecute() list restored after caching");
                }
            } catch (Exception e) {
                Log.d(LOG, "MainActivity:onPostExecute() failed to restore list position");
            }
            Log.d(LOG,"CacheTransactionsTask.onPostExecute()  calls onRefresh()");
            onRefresh();
            Log.d(LOG,"CacheTransactionsTask.onPostExecute() finished");
        }
    }

		/**
		 * @param ruleList - List of rules
		 * @param t if null click will open editor. Otherwise will switch to selected rule.
		 */
    private void showRulePickerDialog(List <Rule> ruleList, Transaction t) {
				Log.d(LOG, "MainActivity:showRulePickerDialog()");
    		if (ruleList != null) {
                // Creating dialog for rule picking
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setCancelable(true);
                builder.setTitle(getString(R.string.action_rule_list));
                builder.setPositiveButton(getString(R.string.action_cancel), (dialog, which) -> pickRuleDialog.dismiss());
                ruleListAdapter = new RuleListAdapter(ruleList, t);
                builder.setAdapter(ruleListAdapter, null);
                pickRuleDialog = builder.create();
                pickRuleDialog.show();
            }
    }

    private void loadMyBanks(){
        Log.d(LOG, "MainActivity:loadMyBanks() started");
        myBanks = db.getBanks(country);
        for (Bank b : myBanks) {
            if (b.isActive()) activeBank = b;
        }
        Log.d(LOG, "MainActivity:loadMyBanks() finished");
    }

		private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener, DialogInterface.OnClickListener cancelListener) {
				new android.support.v7.app.AlertDialog.Builder(this)
								.setMessage(message)
								.setPositiveButton("OK", okListener)
								.setNegativeButton("Cancel", cancelListener)
								.create()
								.show();
		}

}
