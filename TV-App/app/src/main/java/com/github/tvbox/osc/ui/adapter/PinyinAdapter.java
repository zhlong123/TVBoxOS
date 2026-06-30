package com.github.tvbox.osc.ui.adapter;

import android.graphics.Typeface;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.FocusAnimHelper;

import java.util.ArrayList;

public class PinyinAdapter extends BaseQuickAdapter<String, BaseViewHolder> {
    public PinyinAdapter() {
        super(R.layout.item_search_word_hot, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, String item) {
        int position = helper.getLayoutPosition();
        helper.setText(R.id.tvSearchWord, item);
        TextView rank = helper.getView(R.id.tvSearchWordRank);
        rank.setText(String.valueOf(position + 1));
        int rankColorRes;
        if (position == 0) {
            rankColorRes = R.color.search_rank_1;
        } else if (position == 1) {
            rankColorRes = R.color.search_rank_2;
        } else if (position == 2) {
            rankColorRes = R.color.search_rank_3;
        } else {
            rankColorRes = R.color.ui_text_muted;
        }
        rank.setTextColor(helper.itemView.getContext().getColor(rankColorRes));
        rank.setTypeface(null, position < 3 ? Typeface.BOLD : Typeface.NORMAL);
        FocusAnimHelper.attachSearchWordFocus(helper.itemView);
    }
}
