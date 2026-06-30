package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FocusAnimHelper;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ImgUtil;
import com.github.tvbox.osc.util.UiLayoutConfig;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * @author pj567
 * @date :2020/12/21
 * @description:
 */
public class HistoryAdapter extends BaseQuickAdapter<VodInfo, BaseViewHolder> {
    public HistoryAdapter() {
        super(R.layout.item_history, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, VodInfo item) {
        FrameLayout tvDel = helper.getView(R.id.delFrameLayout);
        if (HawkConfig.hotVodDelete) {
            tvDel.setVisibility(View.VISIBLE);
        } else {
            tvDel.setVisibility(View.GONE);
        }

        TextView tvSource = helper.getView(R.id.tvSource);
        SourceBean bean = ApiConfig.get().getSource(item.sourceKey);
        if (bean != null) {
            tvSource.setText(bean.getName());
            tvSource.setVisibility(View.VISIBLE);
        } else {
            tvSource.setText("搜索");
            tvSource.setVisibility(View.VISIBLE);
        }

        TextView tvNote = helper.getView(R.id.tvNote);
        if (item.note == null || item.note.isEmpty()) {
            tvNote.setVisibility(View.GONE);
        } else {
            String note = item.note.trim();
            if (note.length() > 18) {
                note = note.substring(0, 17) + "…";
            }
            tvNote.setText(note);
            tvNote.setVisibility(View.VISIBLE);
        }

        helper.setText(R.id.tvName, item.name);
        ImageView ivThumb = helper.getView(R.id.ivThumb);
        int pxWidth = AutoSizeUtils.mm2px(mContext, ImgUtil.defaultWidth);
        int pxHeight = AutoSizeUtils.mm2px(mContext, ImgUtil.defaultHeight);
        if (!TextUtils.isEmpty(item.pic)) {
            Picasso.get()
                    .load(DefaultConfig.checkReplaceProxy(item.pic))
                    .resize(pxWidth, pxHeight)
                    .centerCrop()
                    .placeholder(R.drawable.img_loading_placeholder)
                    .noFade()
                    .error(ImgUtil.createTextDrawable(item.name))
                    .into(ivThumb);
        } else {
            ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
        }
        UiLayoutConfig.applyPosterItemSize(helper.itemView);
        FocusAnimHelper.attachPosterItemFocus(helper.itemView);
    }
}
