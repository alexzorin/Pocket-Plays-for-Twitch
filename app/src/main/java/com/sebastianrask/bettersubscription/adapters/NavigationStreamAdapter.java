package com.sebastianrask.bettersubscription.adapters;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.sebastianrask.bettersubscription.R;
import com.sebastianrask.bettersubscription.activities.stream.LiveStreamActivity;
import com.sebastianrask.bettersubscription.model.StreamInfo;
import com.sebastianrask.bettersubscription.views.DynamicImageView;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityOptionsCompat;
import androidx.recyclerview.widget.RecyclerView;

public class NavigationStreamAdapter extends RecyclerView.Adapter<NavigationStreamAdapter.NavigationStreamViewHolder> {

    private ArrayList<StreamInfo> elements = new ArrayList<>();
    private Activity activity;

    public NavigationStreamAdapter(Activity a) {
        this.activity = a;
    }

    public void add(StreamInfo ci) {
        elements.add(ci);
        Collections.sort(elements, new Comparator<StreamInfo>() {
            @Override
            public int compare(StreamInfo c0, StreamInfo c1) {
                return new Integer(c1.getCurrentViewers()).compareTo(new Integer(c0.getCurrentViewers()));
            }
        });
    }

    public void clear() {
        elements.clear();
    }

    @NonNull
    @Override
    public NavigationStreamViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.cell_nav_stream, parent, false);

        return new NavigationStreamViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull NavigationStreamViewHolder holder, int position) {
        final StreamInfo ci = elements.get(position);

        holder.message.setText(ci.getChannelInfo().getDisplayName());
        holder.streamCount.setText(String.valueOf(ci.getCurrentViewers()));
        if (ci.getChannelInfo().getLogoURLString() != null) {
            Picasso.get().load(ci.getChannelInfo().getLogoURLString()).into(holder.img);
        }

        final View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean deviceHasLollipop = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;

                final Context ctx = activity;
                final Intent intent = LiveStreamActivity.createLiveStreamIntent(ci,
                        deviceHasLollipop, ctx);
                if (deviceHasLollipop) {
                    View sharedView = view.findViewById(R.id.my_channels_container);
                    sharedView.setTransitionName(ctx.getString(R.string.stream_preview_transition));
                    final ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                            activity, sharedView, ctx.getString(R.string.stream_preview_transition));
                    activity.startActivity(intent, options.toBundle());
                } else {
                    activity.startActivity(intent);
                    activity.overridePendingTransition(R.anim.slide_in_bottom_anim, R.anim.fade_out_semi_anim);
                }
            }
        };
        holder.itemView.findViewById(R.id.my_channels_container).setOnClickListener(listener);
    }

    @Override
    public int getItemCount() {
        return elements.size();
    }

    protected class NavigationStreamViewHolder extends RecyclerView.ViewHolder {

        private TextView message;
        private DynamicImageView img;
        private TextView streamCount;

        public NavigationStreamViewHolder(@NonNull View itemView) {
            super(itemView);
            message = itemView.findViewById(R.id.nav_stream_text);
            img = itemView.findViewById(R.id.nav_stream_image);
            streamCount = itemView.findViewById(R.id.text_navigation_drawer_stream_count);
        }
    }
}
