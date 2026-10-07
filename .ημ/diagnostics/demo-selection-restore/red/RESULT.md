# Scene-switch failure restoration RED

On base c0c76ee26ef5f7155d50d84adeb5761dad6f6c1c, focused production-IntentAtom tests executed with unchanged production demo.clj (SHA256 bc94c5881b8e4decb5e6ec7bb2a797b08b9b6e76160c388efbd75111d62118a2). Test SHA2562a69586decb6d128ed5eb2c3e76a0d9bdd11a8171bf4209d1a58a29d4a51b7e4.

Observed **2 tests / 26 assertions:22 pass,4 expected failures,0 errors**, exit1/reaped2026-10-07T02:40:54.559258Z. Three failures at exact settings restoration after interruption (present/nil/absent); fourth at exact settings plus unrelated configuration mutation after the original wait-read exception. All other exception identity, camera/publication, pending queue and FIFO assertions passed. HEAD and four input hashes unchanged throughout.

This is a meaningful RED for existing select! leaving its temporary pause settings installed. It neither calls the native service nor claims gameplay, cancellation, world rollback or a timing bound. Production GREEN must follow this committed checkpoint. Full demo and strict gates are not run at this RED stage. Existing seven tests remain byte-identical.
