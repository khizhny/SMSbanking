package com.khizhny.smsbanking.model;

import android.content.ContentValues;
import android.support.annotation.NonNull;
import android.util.Log;

import com.khizhny.smsbanking.R;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class Rule implements java.io.Serializable {

	private static final long serialVersionUID = 3; // Is used to indicate class version during Import/Export
	private final static String LOG ="SMS_BANKING";

	private int id=-1;
	private final Bank bank; // back reference to bank
	private String name;
	private String smsBody="";
	private String mask="";
	private Boolean advanced=false;
    private String nameSuggestion="";
	private transactionType ruleType=transactionType.UNKNOWN;
	public List<SubRule> subRuleList= new ArrayList<>();
	public final List<Word> words= new ArrayList<>();

	private int wordsCount=0; // for old rules compatibility
	private boolean[] wordIsSelected= null; // for old rules compatibility

	/**
	 * Transaction type icons array.
	 */
	public static final int[] ruleTypeIcons ={
			R.drawable.ic_transaction_unknown,
			R.drawable.ic_transaction_income,
			R.drawable.ic_transaction_withdraw,
			R.drawable.ic_transaction_transfer_out,
			R.drawable.ic_transaction_transfer_in,
			R.drawable.ic_transaction_shopping,
			R.drawable.ic_transaction_failed,
			R.drawable.ic_transaction_calculated, // Calculated
			R.drawable.ic_transaction_ignore // ignore
	};

	public enum transactionType {
		UNKNOWN,
		INCOME,
		WITHDRAW,
		TRANSFER_IN,
		TRANSFER_OUT,
		PURCHASE,
		FAILED,
		CALCULATED,
		IGNORE
	}

	/**
	 * New rule constructor.
	 * @param bank Bank.
	 * @param name Name of the rule
	 */
    public Rule(Bank bank, String name){
		this.bank=bank;
		bank.ruleList.add(this);
		this.name=name;
		this.nameSuggestion=name;
	}

	/**
	 * Constructor is used for cloning rule object with all Rules,subrules,words
	 * @param bank Bank to bind the rule.
	 * @param originRule Rule to be duplicated
	 */
    public Rule(Rule originRule, Bank bank){
		this.bank=bank;
		bank.ruleList.add(this);
		this.name=originRule.name;
		this.nameSuggestion=originRule.name;
		this.smsBody=originRule.smsBody;
		this.mask=originRule.mask;
		this.advanced=originRule.advanced;
		this.ruleType=originRule.ruleType;
		this.wordsCount=originRule.wordsCount;

		// Cloning subrules
		for (SubRule sr : originRule.subRuleList) {
			this.subRuleList.add(new SubRule(sr, this));
		}
		// Cloning words
		for (Word w : originRule.words) {
			this.words.add(new Word(w, this));
		}
		// For old rules. Cloning selected words
		setSelectedWords(originRule.getSelectedWords());
	}

	public String toString(){
		return name;
	}

    public int getId() {
        return id;
    }

    public Bank getBank() {
        return bank;
    }

	public void setId(int id) {
		this.id = id;
	}

	public String getSmsBody() {
		return smsBody;
	}

	public void setSmsBody(String smsBody) {
		this.smsBody = smsBody;
		wordsCount=smsBody.split(" ").length;
		wordIsSelected =new boolean[wordsCount+2]; // adding 2 words for <BEGIN> and <END>
		for (int i=1; i<=wordsCount;i++) {
			wordIsSelected[i]=false;
		}
		wordIsSelected[0]=true; // words <BEGIN> and <END> is always selected
		wordIsSelected[wordsCount+1]=true;
	}

	public String getMask() {
		return mask;
	}

	public void setMask(String mask) {
		this.mask = mask;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	private String getSelectedWords() {
		if (mask.startsWith("^")) return ""; // selected words is not used any more in new versions.
		StringBuilder r= new StringBuilder();
		String delimiter="";
		for (int i=0; i<=wordsCount+1;i++) {
			if (wordIsSelected[i]) {
				r.append(delimiter).append(i);
				delimiter=",";
			}
		}
		return r.toString();
	}

    public String getRuleNameSuggestion() {
        return nameSuggestion;
    }

	/**
	 * Deprecated method to support old rules (non regexp)
	 * @param selectedWords sets array of selected words
	 */
    public void setSelectedWords(String selectedWords) {
		wordIsSelected=new boolean[wordsCount+2];
    	if (!selectedWords.isEmpty()) {
			// function sets selected words flags from string.
			String[] a = selectedWords.split(",");
			// setting all to false
			for (int i = 0; i <= wordsCount + 1; i++) {
				wordIsSelected[i] = false;
			}
			// setting selected to true
			int k;
			for (String w : a) {
				k = Integer.parseInt(w);
				wordIsSelected[k] = true;
			}

			//old updateMask();
			StringBuilder sb=new StringBuilder();
			String delimiter = "";
			String[] words = smsBody.split(" ");
			if (!smsBody.trim().equals(smsBody)) sb.append(".*");
			boolean skip_wildcard = false;
			for (int i = 1; i <= wordsCount; i++) {
				if (wordIsSelected[i]) {

					sb.append(delimiter).append("\\Q").append(words[i - 1]).append("\\E");
					skip_wildcard = false;
				} else {
					if (!skip_wildcard) {
						sb.append(delimiter).append(".*");
						skip_wildcard = true;
					}
				}
				delimiter = " ";
			}
			if (!smsBody.trim().equals(smsBody)) sb.append(".*");
			mask=sb.toString();
		}
	}

    /**
     * Returns a Transaction example based on origin sms used to create rule and
     * all of the subrules within the rule.
     * @return Transaction object
     */
	public Transaction getSampleTransaction(){
        Transaction transaction = new Transaction(smsBody,bank.getDefaultCurrency(),null);
        applyToTransaction(transaction);
        transaction.calculateMissedData();
        return  transaction;
    }

	/**
	 * Function updates sms mask (used to match SMS and Rules) after user change constant words.
	 */
	public void updateMask(){
		if (advanced) return; // Do not overwrite user defined custom mask if advanced flag is set.
		StringBuilder s=new StringBuilder();
		s.append("^"); // begining
		StringBuilder ns=new StringBuilder(); // default rule name
		String delimiter="";
		String mask_delimiter;  // chars in between words
		int prev_word_end_index=-1;
		for (Word w : words) {

			if (w.getFirstLetterIndex()-prev_word_end_index>=2) {
				mask_delimiter = smsBody.substring(prev_word_end_index + 1, w.getFirstLetterIndex());
			}else{
				mask_delimiter = "";
			}

			s.append(mask_delimiter);
			switch (w.getWordType()) {
				case WORD_CONST:
					s.append("\\Q").append(w.getBody()).append("\\E"); // actual constant word
					ns.append(delimiter).append(w.getBody());
					break;
				case WORD_VARIABLE:
					s.append("(.*)");
					break;
				case WORD_VARIABLE_FIXED_SIZE:
					s.append("(.{").append(w.getBody().length()).append("})");
					break;
			}
			delimiter=" ";
			prev_word_end_index=w.getLastLetterIndex();
		}
		if (smsBody.length()-prev_word_end_index>=2) {
			mask_delimiter = smsBody.substring(prev_word_end_index + 1);
		}else{
			mask_delimiter = "";
		}
		s.append(mask_delimiter).append("$"); // ending
		nameSuggestion=ns.toString();
		mask=s.toString();
		mask=mask.replace("\\E \\Q"," "); // small optimization
	}


	public int getRuleTypeInt() {
		return ruleType.ordinal();
	}

    public transactionType getRuleType() {
        return ruleType;
    }

    /**
	 *
	 * @return Drawable ID of the icon to be shown in transaction list.
	 */
	public int getRuleIconId() {
			int res=ruleTypeIcons[0];
			try {
					return ruleTypeIcons[ruleType.ordinal()];
			}catch (Exception e){
					return res;
			}
	}

	public void setRuleType(int ruleType) {
		this.ruleType = transactionType.values()[ruleType];
	}

	/**
	 * Used just for Regex method
	 * @return list of varible phrases.
	 */
	public List<String> getVariablePhrases(){
		Pattern pattern = Pattern.compile(mask);
		Matcher matcher = pattern.matcher(smsBody);
		List<String> results= new ArrayList<>();
		if (matcher.matches()) {
			for (int i =1; i<=matcher.groupCount();i++){
				results.add(matcher.group(i));
			}
		}
		return results;
	}

	/**
	 *
	 * @return Content values used for database insert or update function.
	 */
	public ContentValues getContentValues(){
		ContentValues v = new ContentValues();
		if (id>=1) v.put("_id",id);
		v.put("name", name);
		v.put("sms_body", smsBody);
		v.put("mask", mask);
		v.put("advanced", (advanced ? -1 : 0));
		v.put("selected_words", getSelectedWords());
		v.put("bank_id", bank.getId());
		v.put("type", ruleType.ordinal());
		return v;
	}

    /**
     * Apply Rule to Transaction.
     * @param transaction Transaction on which rule will be applied.
     */
	void applyToTransaction(@NonNull Transaction transaction ){
		String sms_body = transaction.getSmsBody();
		if (ruleType!=Rule.transactionType.IGNORE && sms_body.matches(mask)) {
			transaction.icon = getRuleIconId();
			for (SubRule subRule : subRuleList) SubRule.applySubRule(subRule,sms_body, transaction);
		}
	}

	public Boolean hasIgnoreType(){
		return ruleType == Rule.transactionType.IGNORE;
	}

    /**
     * Function gets existing subrule for parameter or creates a new one for it.
     * @param transactionParameter - Which transaction parameter is calculated by subrule
     * @return Subrule for calculating defined transactionParameter
     */
	public SubRule getOrCreateSubRule(Transaction.Parameters transactionParameter){
        // looking for existing subrule.
        for (SubRule sr: subRuleList) {
            if (sr.getExtractedParameter()==transactionParameter){
                return sr;
            }
        }
        // if no luck - creating it.
        SubRule sr = new SubRule(this,transactionParameter);
        subRuleList.add(sr);
        return sr;
    }

	public static void makeInitialWordSplitting(Rule r){
		r.words.clear();
		char ch;
		char splitChar=' ';
		boolean WeNeedToSaveWord=false;
		int word_start=0;
		int word_end;

		for (int i=0; i<r.smsBody.length();i++) {
			ch=r.smsBody.charAt(i);
			if (ch==splitChar) {
				if (WeNeedToSaveWord) {
					// we are at the end of word
					word_end=i-1;
						r.words.add(new Word(r,word_start,word_end, Word.WORD_TYPES.WORD_CONST));
				}
				WeNeedToSaveWord=false;
			} else {
				if (!WeNeedToSaveWord) {
					// we are at then begining of word
					word_start=i;
				}
				WeNeedToSaveWord=true;
			}
		}
		if (WeNeedToSaveWord) {
			word_end=r.smsBody.length()-1;
				r.words.add(new Word(r,word_start,word_end, Word.WORD_TYPES.WORD_CONST));
		}
	}

	public void mergeRight(Word w){
		int index= words.indexOf(w);
		int very_last_index=smsBody.length()-1;
		if (index >=words.size()-1) { // No words to the right.
			if (very_last_index!=w.getLastLetterIndex()){
				// some chars to the right. Merging them to body.
				w.reAssign(w.getFirstLetterIndex(),very_last_index);
			}
		}else {  // there is a word to the right
			Word nextWord = words.get(index+1);
			w.reAssign(w.getFirstLetterIndex(),nextWord.getLastLetterIndex());
			words.remove(nextWord);
		}
	}

	public void mergeLeft(Word w){
		int index= words.indexOf(w);
		if (index ==0) { // No words to the left.
			if (w.getFirstLetterIndex()!=0){
				// some chars to the left. Merging them to body.
				w.reAssign(0,w.getLastLetterIndex());
			}
		}else {  // there is a word to the left
			Word prevWord = words.get(index-1);
			w.reAssign(prevWord.getFirstLetterIndex(),w.getLastLetterIndex());
			words.remove(prevWord);
		}
	}

	public void split(Word w, int shift){
		Word newWord = new Word(this,w.getFirstLetterIndex()+shift ,w.getLastLetterIndex(), Word.WORD_TYPES.WORD_CONST);
		w.reAssign(w.getFirstLetterIndex(),shift+w.getFirstLetterIndex()-1);
		words.add(words.indexOf(w)+1,newWord);
	}

	public String getValues(){
		StringBuilder r= new StringBuilder();
		String delim="";
		Pattern pattern = Pattern.compile(mask);
		Matcher matcher = pattern.matcher(smsBody);
		if (matcher.matches()) {
			for (int i = 1; i <= matcher.groupCount(); i++) {
				r.append(delim).append(matcher.group(i));
				delim="\n";
			}
			return r.toString();
		}else{
			return "incorrect mask";
		}

	}

	public void  setAdvanced(int advancedInt){
		advanced = (advancedInt!=0);
	}

	public boolean isAdvanced(){
		return advanced;
	}

		/**
		 * Removes personal data from rule
		 * Works just in regular mode.
		 */
	public static void impersonalize(Rule r){
			if (!r.advanced) {
					for (Word w : r.words) {
							switch (w.getWordType()) {
									case WORD_CONST:
											break;
									case WORD_VARIABLE:
									case WORD_VARIABLE_FIXED_SIZE:
											try {
													String body1 = w.getFirstLetterIndex() == 0 ? "" : r.smsBody.substring(0, w.getFirstLetterIndex());
													String body2 = w.getLastLetterIndex() == (r.smsBody.length() - 1) ? "" : r.smsBody.substring(w.getLastLetterIndex() + 1);
													w.setBody(w.getImpersonalizedBody());
													r.smsBody = (body1 + w.getBody() + body2);
											} catch (Exception e) {
													e.printStackTrace();
													Log.d(LOG,"impersonalize error");
											}
											break;
							}
					}
			}
	}
}

