// INetSpeedInterface.aidl
package app.yomu.netbar.netspeed;

import app.yomu.netbar.netspeed.NetSpeedConfiguration;

// Declare any non-default types here with import statements

interface INetSpeedInterface {

    /**
     * 更新配置
     */
    void updateConfiguration(in NetSpeedConfiguration configuration);

}