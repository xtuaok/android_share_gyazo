package xtuaok.sharegyazo;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.service.chooser.ChooserTarget;
import android.service.chooser.ChooserTargetService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@TargetApi(23)
public class ProfileChooserService extends ChooserTargetService {

    public ProfileChooserService() {
    }

    @Override
    public List<ChooserTarget> onGetChooserTargets(ComponentName targetActivityName, IntentFilter matchedFilter) {
        ProfileManager pm = ProfileManager.getInstance(getApplicationContext());
        ArrayList<Profile> profiles = (ArrayList<Profile>) pm.getProfiles().clone();

        final List<ChooserTarget> targets = new ArrayList<>();
        for (int i = 0; i < profiles.size(); i++) {
            // Name
            final String title = profiles.get(i).getName();
            // Individual icon
            final Icon icon = Icon.createWithResource(this, R.drawable.gyazo_ninja);
            // TODO: Scoring
            final float score = pm.getChooserScore(profiles.get(i));
            // Extras
            final Bundle extra = new Bundle();
            extra.putString("uuid", profiles.get(i).getUUID());
            final ChooserTarget target = new ChooserTarget(title, icon, score, targetActivityName, extra);
            targets.add(target);
        }
        Collections.sort(targets, new targetComparator());
        return targets;
    }

    private static class targetComparator implements Comparator<ChooserTarget> {
        public int compare(ChooserTarget a, ChooserTarget b) {
            float no1 = a.getScore();
            float no2 = b.getScore();

            if (no1 < no2) {
                return 1;
            } else if (no1 == no2) {
                return 0;
            } else {
                return -1;
            }
        }
    }
}
