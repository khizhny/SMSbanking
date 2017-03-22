package com.khizhny.smsbanking;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatSpinner;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

import static com.khizhny.smsbanking.MyApplication.LOG;

public class SubRuleListActivity extends AppCompatActivity{

    private List<SubRule> subRuleList;
	private SubRuleListAdapter adapter;
	private Rule rule;
	protected static List<String> phrases;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_sub_rule);

	}
	@Override
	protected void onPause() {
		super.onPause();
		// Saving all then subrules in our list.
        int subRulesCount = adapter.getCount();
        DatabaseAccess db = DatabaseAccess.getInstance(this);
        db.open();
		for (int i=0; i<subRulesCount;i++){
            db.addOrEditSubRule(adapter.getItem(i));
		}
        db.close();
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.d(LOG,"SubRuleListActivity resuming");
		// getting Rule ID from Intent
		Intent intent = getIntent();
		int ruleId = intent.getExtras().getInt("rule_id");

		Log.d(MyApplication.LOG,"Getting subRules from db to array list");//
		subRuleList=new ArrayList<SubRule>();
        DatabaseAccess db = DatabaseAccess.getInstance(this);
        db.open();
        rule=db.getRule(ruleId);
		subRuleList=db.getSubRules(ruleId);
        db.close();
		//
		phrases=rule.getConstantPhrases();

		Log.d(LOG,"inflating list view with subrules");
		adapter  = new SubRuleListAdapter(this, subRuleList);
		adapter.rule=rule;
		ListView listView = (ListView) findViewById(R.id.sub_rule_list);
        if (listView != null) {
            listView.setAdapter(adapter);
        }

        TextView smsTextView=(TextView) findViewById(R.id.Rule_name_label);
		String smsText=getString(R.string.begin)+rule.getSmsBody()+getString(R.string.end);
        if (smsTextView != null) {
            smsTextView.setText(smsText);
        }

        Button doneButton = (Button) findViewById(R.id.finish_rule);
        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // closing activity and gount back to MainActivity
                finish();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.subrules, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
       switch (item.getItemId()) {

           case R.id.item_new_subrule:
               // addin new SubRule to the list and db.
               DatabaseAccess db = DatabaseAccess.getInstance(SubRuleListActivity.this);
               db.open();
               SubRule sr = new SubRule(rule.getId());
               sr.setId(db.addOrEditSubRule(sr));
               subRuleList.add(sr);
               db.close();
               adapter.notifyDataSetChanged();
               return true;

           case R.id.item_help:
               Intent intent = new Intent(SubRuleListActivity.this, TipActivity.class);
               intent.putExtra("tip_res_id", R.string.tip_subrules_1);
               startActivity(intent);
               return true;
           default:
               return super.onOptionsItemSelected(item);
       }
    }

    private class SubRuleListAdapter extends ArrayAdapter<SubRule> {

        private final List<SubRule> subRuleList;
        public Rule rule;
        private boolean doNotDoEvents;   // this flag is used to supress list refreshing multiple times while first loading

        SubRuleListAdapter(Context context, List<SubRule> subRuleList) {
            super(context, R.layout.activity_sub_rule_list_row, subRuleList);
            this.subRuleList = subRuleList;
        }

        @NonNull
        @Override
        public View getView(final int position, View rowView, @NonNull final ViewGroup parent) {
            Log.d(LOG, "==Position "+position+" Start drawing");
            LayoutInflater vi= (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            if (rowView == null) {
                rowView = vi.inflate(R.layout.activity_sub_rule_list_row, parent, false);
            } else  {
                rowView = vi.inflate(R.layout.activity_sub_rule_list_row, parent, false);
            }

            SubRule sr = subRuleList.get(position);
            doNotDoEvents = true;
            //========================================================================================
            CheckBox activeView = (CheckBox) rowView.findViewById(R.id.sub_rule_active);
            activeView.setChecked(true);
            activeView.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton v, boolean isChecked) {
                    // Activate checkbox listener
                    if (!isChecked) {
                        Log.v(LOG, "position " + position + " activeView triggered.");
                        int subRuleId = subRuleList.get(position).getId();
                        DatabaseAccess db = DatabaseAccess.getInstance(v.getContext());
                        db.open();
                        db.deleteSubRule(subRuleId);
                        db.close();
                        subRuleList.remove(position);
                        notifyDataSetChanged();
                    }
                }
            });

            CheckBox negateView = (CheckBox) rowView.findViewById(R.id.sub_rule_negate);
            negateView.setChecked(sr.isNegate());
            negateView.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton v, boolean isChecked) {
                    // Adding Negate checkbox listener
                    if (!doNotDoEvents && isChecked != subRuleList.get(position).isNegate()) {
                        Log.v(LOG, "position " + position + " negateView triggered.");
                        SubRule subrule = subRuleList.get(position);
                        subrule.setNegate(isChecked);
                        refreshResult((View) v.getParent().getParent(), subrule, rule.getSmsBody());
                    }
                }
            });

            //========================================================================================
            AppCompatSpinner parameterView = (AppCompatSpinner) rowView.findViewById(R.id.sub_rule_parameter);
            parameterView.setSelection(sr.getExtractedParameterInt());
            parameterView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View v, int selectedPosition, long id) {
                    // Adding Parameter spinner listener
                    if (!doNotDoEvents && subRuleList.get(position).getExtractedParameterInt() != selectedPosition) {
                        Log.v(LOG, "position " + position + " ParameterSpinner triggered.");
                        SubRule subrule = subRuleList.get(position);
                        subrule.setExtractedParameter(selectedPosition);
                        refreshResult((View)parentView.getParent().getParent(), subrule, rule.getSmsBody());
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                }
            });

            //========================================================================================
            AppCompatSpinner methodView = (AppCompatSpinner) rowView.findViewById(R.id.sub_rule_method);
            methodView.setSelection(sr.getExtractionMethodInt());
            methodView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                // method Spinner change listener
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int selectedPosition, long id) {
                    Log.d(LOG, "position " + position + " MethodSpinner edited.");
                    if (!doNotDoEvents && subRuleList.get(position).getExtractionMethodInt() != selectedPosition) {
                        SubRule subrule = subRuleList.get(position);
                        Log.d(LOG, "position " + position + " MethodSpinner triggered. Method changed.");
                        subrule.setExtractionMethod(selectedPosition);
                        notifyDataSetChanged();
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                }
            });

            //========================================================================================
            AppCompatSpinner leftNView = (AppCompatSpinner) rowView.findViewById(R.id.sub_rule_left_n);
            leftNView.setSelection(sr.getDistanceToLeftPhrase() - 1);
            leftNView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int selectedPosition, long id) {
                    // Left N value change listener
                    if (!doNotDoEvents && (selectedPosition + 1) != subRuleList.get(position).getDistanceToLeftPhrase()) {
                        Log.v(LOG, "LeftNspinner in position " + position + " triggered.");
                        SubRule subrule = subRuleList.get(position);
                        subrule.setDistanceToLeftPhrase(selectedPosition + 1);
                        refreshResult((View)parentView.getParent().getParent(), subrule, rule.getSmsBody());
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                }
            });

            //========================================================================================
            AppCompatSpinner rightNView = (AppCompatSpinner) rowView.findViewById(R.id.sub_rule_right_n);
            rightNView.setSelection(sr.getDistanceToRightPhrase() - 1);
            rightNView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                // Right N value change listener
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int selectedPosition, long id) {
                    if (!doNotDoEvents && (selectedPosition + 1) != subRuleList.get(position).getDistanceToRightPhrase()) {
                        Log.v(LOG, "RightNspinner in position " + position + " triggered.");
                        SubRule subrule = subRuleList.get(position);
                        subrule.setDistanceToRightPhrase(selectedPosition + 1);
                        refreshResult((View)parentView.getParent().getParent(), subrule, rule.getSmsBody());
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                }
            });

            ArrayAdapter<String> PhraseAdapter = new ArrayAdapter<String>(SubRuleListActivity.this, android.R.layout.simple_spinner_item, SubRuleListActivity.phrases);
            PhraseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            //========================================================================================
            AppCompatSpinner leftPhraseView = (AppCompatSpinner) rowView.findViewById(R.id.sub_rule_left_phrase);
            leftPhraseView.setAdapter(PhraseAdapter);
            leftPhraseView.setSelection(SubRuleListActivity.phrases.indexOf(sr.getLeftPhrase()));
            leftPhraseView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View v, int selectedPosition, long id) {
                    //Left phrase spinner change listener
                    if (!doNotDoEvents && !SubRuleListActivity.phrases.get(selectedPosition).equals(subRuleList.get(position).getLeftPhrase())) {
                        Log.v(LOG, "LeftPhraseSpinner in position " + position + " triggered.");
                        SubRule subrule = subRuleList.get(position);
                        subrule.setLeftPhrase(SubRuleListActivity.phrases.get(selectedPosition));
                        refreshResult((View)parentView.getParent().getParent(), subrule, rule.getSmsBody());
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                }
            });

            //========================================================================================
            AppCompatSpinner rightPhraseView = (AppCompatSpinner) rowView.findViewById(R.id.sub_rule_right_phrase);
            rightPhraseView.setAdapter(PhraseAdapter);
            rightPhraseView.setSelection(SubRuleListActivity.phrases.indexOf(sr.getRightPhrase()));
            rightPhraseView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int selectedPosition, long id) {
                    Log.d(LOG, "RightPhraseSpinner in position " + position + " triggered.");
                    if (!doNotDoEvents && !SubRuleListActivity.phrases.get(selectedPosition).equals(subRuleList.get(position).getRightPhrase())) {
                        SubRule subrule = subRuleList.get(position);
                        subrule.setRightPhrase(SubRuleListActivity.phrases.get(selectedPosition));
                        refreshResult((View)parentView.getParent().getParent(), subrule, rule.getSmsBody());
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                }
            });

            //========================================================================================
            TextView constantView = (TextView) rowView.findViewById(R.id.sub_rule_constant_value);
            switch (sr.getExtractionMethod()) {
                case WORD_AFTER_PHRASE:
                case WORD_BEFORE_PHRASE:
                case WORDS_BETWEEN_PHRASES:
                    constantView.setText("");
                    break;
                case USE_CONSTANT:
                    constantView.setText(sr.getConstantValue());
                    break;
            }

            constantView.setOnEditorActionListener(
                    new EditText.OnEditorActionListener() {
                        @Override
                        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                            Log.d(LOG, "position " + position + " CONSANT field onEditorAction() fired.");

                            if (event != null && event.getAction() != KeyEvent.ACTION_DOWN) {
                                return false;
                            } else if (actionId == EditorInfo.IME_ACTION_SEARCH
                                    || event == null
                                    || event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                                // the user is done typing.
                                Log.d(LOG, "position " + position + " Constant field changed.");
                                if (subRuleList.get(position).getExtractionMethod() == SubRule.Method.USE_CONSTANT) {
                                    subRuleList.get(position).setConstantValue(v.getText().toString());
                                    notifyDataSetChanged();
                                }
                                return true;
                            }
                            return false; // pass on to other listeners.
                        }
                    });/**/


            //========================================================================================
            TextView resultView = (TextView) rowView.findViewById(R.id.sub_rule_result_value);
            Log.d(LOG, "Rezult calculated for position " + position);
            refreshResult(resultView,sr, rule.getSmsBody());
            //========================================================================================
            AppCompatSpinner separatorView = (AppCompatSpinner) rowView.findViewById(R.id.sub_rule_separator);
            separatorView.setSelection(sr.getDecimalSeparator());
            separatorView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int selectedPosition, long id) {
                    Log.d(LOG,"SeparatorView in position " + position + " triggered.");
                    if (subRuleList.get(position).getDecimalSeparator() != selectedPosition) {
                        SubRule subrule=subRuleList.get(position);
                        subrule.setDecimalSeparator(selectedPosition);
                        //TextView resultView = (TextView) parent.getChildAt(position).findViewById(R.id.sub_rule_result_value);
                        refreshResult((View)parentView.getParent().getParent(),subrule,rule.getSmsBody());
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                }
            });

            //========================================================================================
            AppCompatSpinner ignoreNLeftView = (AppCompatSpinner) rowView.findViewById(R.id.sub_rule_ignore_n_first);
            ignoreNLeftView.setSelection(sr.getTrimLeft());
            ignoreNLeftView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int selectedPosition, long id) {
                    Log.d(LOG,"IgnoreNleft in position " + position + " triggered.");
                    if (subRuleList.get(position).getTrimLeft() != selectedPosition) {
                        SubRule subrule=subRuleList.get(position);
                        subrule.setTrimLeft(selectedPosition);
                        //TextView resultView = (TextView) parent.getChildAt(position).findViewById(R.id.sub_rule_result_value);
                        refreshResult((View)parentView.getParent().getParent(),subrule,rule.getSmsBody());
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                }
            });

            //========================================================================================
            AppCompatSpinner ignoreNRightView = (AppCompatSpinner) rowView.findViewById(R.id.sub_rule_ignore_n_last);
            ignoreNRightView.setSelection(sr.getTrimRight());
            ignoreNRightView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int selectedPosition, long id) {
                    Log.d(LOG,"IgnoreNright in position " + position + " triggered.");
                    if (subRuleList.get(position).getTrimRight() != selectedPosition) {
                        SubRule subrule=subRuleList.get(position);
                        subrule.setTrimRight(selectedPosition);
                        //TextView resultView = (TextView) parent.getChildAt(position).findViewById(R.id.sub_rule_result_value);
                        refreshResult((View)parentView.getParent().getParent(),subrule,rule.getSmsBody());
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                }
            });

            // hiding unused views depending on used method
            switch (sr.getExtractionMethod()){
                case WORD_AFTER_PHRASE:
                    rowView.findViewById(R.id.sub_rule_left_ll).setVisibility(View.VISIBLE);
                    rowView.findViewById(R.id.sub_rule_right_ll).setVisibility(View.GONE);
                    rowView.findViewById(R.id.sub_rule_trim_ll).setVisibility(View.VISIBLE);
                    rowView.findViewById(R.id.sub_rule_const_ll).setVisibility(View.GONE);
                    break;
                case WORD_BEFORE_PHRASE:
                    rowView.findViewById(R.id.sub_rule_left_ll).setVisibility(View.GONE);
                    rowView.findViewById(R.id.sub_rule_right_ll).setVisibility(View.VISIBLE);
                    rowView.findViewById(R.id.sub_rule_trim_ll).setVisibility(View.VISIBLE);
                    rowView.findViewById(R.id.sub_rule_const_ll).setVisibility(View.GONE);
                    break;
                case WORDS_BETWEEN_PHRASES:
                    rowView.findViewById(R.id.sub_rule_left_ll).setVisibility(View.VISIBLE);
                    rowView.findViewById(R.id.sub_rule_right_ll).setVisibility(View.VISIBLE);
                    rowView.findViewById(R.id.sub_rule_trim_ll).setVisibility(View.VISIBLE);
                    rowView.findViewById(R.id.sub_rule_const_ll).setVisibility(View.GONE);
                    break;
                case USE_CONSTANT:
                    rowView.findViewById(R.id.sub_rule_left_ll).setVisibility(View.GONE);
                    rowView.findViewById(R.id.sub_rule_right_ll).setVisibility(View.GONE);
                    rowView.findViewById(R.id.sub_rule_trim_ll).setVisibility(View.GONE);
                    rowView.findViewById(R.id.sub_rule_const_ll).setVisibility(View.VISIBLE);
                    break;
            }

            doNotDoEvents=false;
            Log.d(LOG,"==Finished drawing list item number " + position);
            return rowView;
        }

        private void refreshResult(View rowView, SubRule subrule, String msg){
            TextView resultView = (TextView) rowView.findViewById(R.id.sub_rule_result_value);
            Log.d(LOG,"refreshResult is called");
            switch (subrule.getExtractedParameter()){
                case CURRENCY:
                    resultView.setText(subrule.applySubRule(msg, 1));
                    break;
                case ACCOUNT_STATE_BEFORE:
                case ACCOUNT_STATE_AFTER:
                case ACCOUNT_DIFFERENCE:
                case COMMISSION:
                    resultView.setText(subrule.applySubRule(msg, 0));
                    break;
                case EXTRA_1:
                case EXTRA_2:
                case EXTRA_3:
                case EXTRA_4:
                    resultView.setText(subrule.applySubRule(msg, 2));
                    break;
            }
        }
    }
}
